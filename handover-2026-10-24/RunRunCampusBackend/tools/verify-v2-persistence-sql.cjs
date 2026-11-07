// 对照冻结清单，逐分支核对 SQL token 与绑定参数顺序；不替代真实 PostgreSQL 测试。
const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '../../..');
const backend = path.resolve(__dirname, '..');
const inventory = JSON.parse(fs.readFileSync(path.join(root,
    'docs/reports/v2-mybatis-plus/persistence-inventory.json'), 'utf8'));
let checked = 0;
const exceptions = [];
const baseMapperMethods = new Set([
    'ServiceConfigMapper.findZones', 'ServiceConfigMapper.findEnabledZone',
    'IdentityMapper.insertAccount', 'IdentityMapper.insertPendingCertification',
    'CourierCertificationV2Mapper.insertPendingCertification'
]);
function expression(value, constants, flags, variables = {}) {
    if (value.text !== undefined) {
        return value.text;
    }
    if (value.op === '+') {
        return expression(value.left, constants, flags, variables) + expression(value.right, constants, flags, variables);
    }
    if (value.condition) {
        return expression(flags ? value.then : value.else, constants, flags, variables);
    }
    const constant = constants.find(field => field.name === value.java);
    if (constant) {
        return expression(constant.value, constants, flags, variables);
    }
    if (variables[value.java] !== undefined) {
        return variables[value.java];
    }
    throw new Error('需人工核对的动态 SQL: ' + value.java);
}
function xmlSql(value, flag, status = flag) {
    return value.replace(/<choose>\s*<when test="[^"]+">([\s\S]*?)<\/when>\s*<otherwise>([\s\S]*?)<\/otherwise>\s*<\/choose>/g,
        (_, yes, no) => flag ? yes : no)
        .replace(/<if test="([^"]+)">([\s\S]*?)<\/if>/g,
            (_, test, body) => (test.startsWith('!') ? !flag : test === 'status != null' ? status : flag) ? body : '')
        .replaceAll('&lt;', '<').replaceAll('&gt;', '>').replaceAll('&amp;', '&');
}
function tokens(sql) {
    return sql.match(/'(?:''|[^'])*'|"(?:""|[^"])*"|\|\||::|<=|>=|<>|!=|[a-zA-Z_][\w$]*|\d+(?:\.\d+)?|[^\s]/g);
}
const items = inventory.items.flatMap(item => {
    if (item.method === 'findEnabled' && item.mapper.endsWith('ServiceConfigMapper')) {
        return ['Zone', 'ServicePoint'].map(kind => ({...item, method: 'findEnabled' + kind,
            variables: {table: kind === 'Zone' ? 'delivery_zones' : 'service_points'}}));
    }
    if (item.method === 'updateOrder' && item.delegatingMethods) {
        return item.delegatingMethods.map(method => ({...item, method: method.name,
            variables: {assignment: method.source.match(/updateOrder\("([^"]+)"/)[1]}}));
    }
    return [item];
});
for (const item of items) {
    const pieces = item.mapper.split('.');
    let name = pieces.pop();
    const domain = pieces.pop();
    let method = item.method;
    if (name === 'OrderStore') {
        name = 'OrderReadMapper';
        method = 'findOrder';
    }
    if (name === 'FulfillmentMapper' && method === 'findLocationEvent') {
        method = 'readLocationEvent';
    }
    const xmlPath = path.join(backend, 'src/main/resources/mapper/v2', domain, name + '.xml');
    if (!fs.existsSync(xmlPath)) {
        throw new Error('未找到迁移后的 XML: ' + item.mapper);
    }
    const xml = fs.readFileSync(xmlPath, 'utf8');
    const body = xml.match(new RegExp('<(?:select|insert|update|delete) id="' + method
        + '"[^>]*>([\\s\\S]*?)</(?:select|insert|update|delete)>'))?.[1];
    if (!body) {
        if (!baseMapperMethods.has(name + '.' + method)) {
            throw new Error('缺少 SQL 映射: ' + item.mapper + '.' + item.method);
        }
        exceptions.push(item.mapper + '.' + item.method + ': BaseMapper/Wrapper；由真实数据库用例验证项目映射');
        continue;
    }
    const scenarios = name === 'FulfillmentQueryMapper'
        ? [[false, false], [false, true], [true, false], [true, true]]
        : [[false, false], [true, true]];
    for (const [flag, status] of scenarios) {
        const call = item.originalJdbcCalls[0];
        const variables = {...item.variables};
        let dynamicParameters;
        if (name === 'FulfillmentQueryMapper') {
            const tasks = method === 'findCourierTasks';
            // 初始条件、追加条件及顺序取自冻结的 originalMethod，逐一展开四种组合。
            variables.where = tasks ? item.originalMethod.match(/String where = "([^"]+)"/)[1]
                : "o.status = 'PENDING_ASSIGNMENT' AND EXISTS (SELECT 1 FROM runrun_v2.payment_orders p WHERE p.order_id = o.id AND p.purpose = 'ORIGINAL' AND p.status = 'PAID')";
            dynamicParameters = tasks ? ['courierId'] : [];
            if (tasks && status) {
                variables.where += ' AND t.status=?';
                dynamicParameters.push('status');
            }
            if (flag) {
                variables.where += tasks ? ' AND (t.created_at,t.id)<(?,?)' : ' AND (o.created_at,o.id)<(?,?)';
                dynamicParameters.push('afterCreatedAt', 'afterId');
            }
            dynamicParameters.push('limit');
        }
        let original;
        try {
            original = expression(call.args[0], item.sqlConstants, flag, variables);
        } catch (error) {
            throw new Error(item.mapper + '.' + item.method + ': ' + error.message);
        }
        const offset = ['query', 'queryForObject'].includes(call.kind)
            || (call.kind === 'queryForList' && call.args[1]?.java?.endsWith('.class')) ? 2 : 1;
        const expectedParameters = dynamicParameters || call.args.slice(offset)
            .map(arg => arg.java === 'ResourceIds.parse(orderId)' ? 'orderId' : arg.java);
        const actual = xmlSql(body, flag, status);
        const actualParameters = [...actual.matchAll(/#\{([^,}]+)[^}]*\}/g)].map(match => match[1]);
        const normalized = actual.replace(/#\{[^}]*\}/g, '?');
        if (JSON.stringify(tokens(original)) !== JSON.stringify(tokens(normalized))
            || JSON.stringify(expectedParameters) !== JSON.stringify(actualParameters)) {
            throw new Error(item.mapper + '.' + item.method + ' SQL/参数不等价，分支 ' + flag);
        }
        checked++;
    }
}
console.log('PASS: ' + checked + ' 次 SQL 展开的 token、参数数量及顺序一致。');
console.log('BaseMapper/Wrapper 项（数据库用例覆盖）：\n' + [...new Set(exceptions)].join('\n'));
