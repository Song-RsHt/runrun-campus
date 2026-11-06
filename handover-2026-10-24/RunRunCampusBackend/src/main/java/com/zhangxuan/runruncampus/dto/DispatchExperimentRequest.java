package com.zhangxuan.runruncampus.dto;

import java.util.List;

/** 派单实验请求参数；保留入口的字段、构造器及校验契约。 */
public record DispatchExperimentRequest(List<DispatchScenario> scenarios) {}
