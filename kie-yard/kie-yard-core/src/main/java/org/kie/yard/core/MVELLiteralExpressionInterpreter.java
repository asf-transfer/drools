package org.kie.yard.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.mvel2.MVEL;

import java.util.HashMap;
import java.util.Map;

public class MVELLiteralExpressionInterpreter implements Firable {
    private final String name;
    private final QuotedExprParsed expr;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public MVELLiteralExpressionInterpreter(final String name,
                                            final QuotedExprParsed expr) {
        this.name = name;
        this.expr = expr;
    }

    @Override
    public int fire(final Map<String, Object> context,
                    final YaRDDefinitions units) {
        final Map<String, Object> internalContext = new HashMap<>();
        internalContext.putAll(context);

        for (Map.Entry<String, StoreHandle<Object>> outKV : units.outputs().entrySet()) {
            if (!outKV.getValue().isValuePresent()) {
                continue;
            }
            internalContext.put(QuotedExprParsed.escapeIdentifier(outKV.getKey()), outKV.getValue().get());
        }

        try {
            String rewrittenExpression = expr.getRewrittenExpression();
            final Object result = MVEL.eval(rewrittenExpression, internalContext);
            units.outputs().get(name).set(resolveValue(result));
            return 1;
        } catch (Exception e) {
            throw new RuntimeException("interpretation failed at runtime", e);
            // TODO why throw and not return 0?
        }
    }

    private Object resolveValue(Object value) {
        try {
            if (value instanceof String text) {
                return jsonMapper.readValue(text, Map.class);
            }
        } catch (JsonProcessingException ignored) {
        }
        return value;
    }
}
