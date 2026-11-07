// 对照冻结清单，逐分支核对 SQL token 与绑定参数顺序；不替代真实 PostgreSQL 测试。
const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '../../..');
const backend = path.resolve(__dirname, '..');
const inventory = JSON.parse(fs.readFileSync(path.join(root,
    'docs/reports/v2-mybatis-plus/persistence-inventory.json'), 'utf8'));
let checked = 0;
const exceptions = [];
function expression(value, constants, flags) {
    if (value.text !== undefined) {
        return value.text;
    }
    if (value.op === '+') {
        return expression(value.left, constants, flags) + expression(value.right, constants, flags);
    }
    if (value.condition) {
        return expression(flags ? value.then : value.else, constants, flags);
    }
    const constant = constants.find(field => field.name === value.java);
    if (constant) {
        return expression(constant.value, constants, flags);
    }
    throw new Error('需人工核对的动态 SQL: ' + value.java);
}
function xmlSql(value, flag) {
    return value.replace(/<choose>\s*<when test="[^"]+">([\s\S]*?)<\/when>\s*<otherwise>([\s\S]*?)<\/otherwise>\s*<\/choose>/g,
        (_, yes, no) => flag ? yes : no)
        .replace(/<if test="([^"]+)">([\s\S]*?)<\/if>/g,
            (_, test, body) => (test.startsWith('!') ? !flag : flag) ? body : '')
        .replaceAll('&lt;', '<').replaceAll('&gt;', '>').replaceAll('&amp;', '&');
}
function tokens(sql) {
    return sql.match(/'(?:''|[^'])*'|"(?:""|[^"])*"|\|\||::|<=|>=|<>|!=|[a-zA-Z_][\w$]*|\d+(?:\.\d+)?|[^\s]/g);
}
for (const item of inventory.items) {
    const pieces = item.mapper.split('.');
    const name = pieces.pop();
    const domain = pieces.pop();
    const xmlPath = path.join(backend, 'src/main/resources/mapper/v2', domain, name + '.xml');
    if (!fs.existsSync(xmlPath)) {
        exceptions.push(item.mapper + '.' + item.method + ': 尚未迁移或由共享组件委托');
        continue;
    }
    const xml = fs.readFileSync(xmlPath, 'utf8');
    const body = xml.match(new RegExp('<(?:select|insert|update|delete) id="' + item.method
        + '"[^>]*>([\\s\\S]*?)</(?:select|insert|update|delete)>'))?.[1];
    if (!body) {
        exceptions.push(item.mapper + '.' + item.method + ': BaseMapper/Wrapper 或受控分表，另行核对');
        continue;
    }
    for (const flag of [false, true]) {
        const call = item.originalJdbcCalls[0];
        let original;
        try {
            original = expression(call.args[0], item.sqlConstants, flag);
        } catch (error) {
            exceptions.push(item.mapper + '.' + item.method + ': ' + error.message);
            break;
        }
        const offset = ['query', 'queryForObject'].includes(call.kind)
            || (call.kind === 'queryForList' && call.args[1]?.java?.endsWith('.class')) ? 2 : 1;
        const expectedParameters = call.args.slice(offset).map(arg => arg.java);
        const actual = xmlSql(body, flag);
        const actualParameters = [...actual.matchAll(/#\{([^,}]+)[^}]*\}/g)].map(match => match[1]);
        const normalized = actual.replace(/#\{[^}]*\}/g, '?');
        if (JSON.stringify(tokens(original)) !== JSON.stringify(tokens(normalized))
            || JSON.stringify(expectedParameters) !== JSON.stringify(actualParameters)) {
            throw new Error(item.mapper + '.' + item.method + ' SQL/参数不等价，分支 ' + flag);
        }
        checked++;
    }
}
console.log('PASS: ' + checked + ' 个 SQL 分支的 token、参数数量及顺序一致。');
console.log('单独核对项：\n' + [...new Set(exceptions)].join('\n'));
