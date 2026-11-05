package com.yingchang.warehouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.*;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;

/** 输入格式由接口边界统一校验；业务服务负责库存及单据状态规则。 */
@Component
public class RequestSchemas {
    private final ObjectMapper json;
    private final Map<String, JsonSchema> schemas = new LinkedHashMap<>();
    public RequestSchemas(ObjectMapper json) throws Exception {
        this.json = json;
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        SchemaValidatorsConfig config = new SchemaValidatorsConfig();
        config.setFormatAssertionsEnabled(true);
        try (var input = getClass().getResourceAsStream("/request-schemas.json")) {
            JsonNode root = json.readTree(input);
            root.fields().forEachRemaining(e -> schemas.put(e.getKey(), factory.getSchema(e.getValue(), config)));
        }
    }
    public Data validate(String name, Data value) {
        var errors = schemas.get(name).validate(json.valueToTree(value));
        if (!errors.isEmpty()) throw new BusinessError("VALIDATION_ERROR", "请检查必填项和填写格式", 400,
            errors.stream().map(ValidationMessage::getMessage).toList());
        return value;
    }
}
