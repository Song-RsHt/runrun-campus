package com.zhangxuan.runrunv2.vo;

import java.util.List;






public record CursorPageVO<T>(List<T> items, String nextCursor) {
}
