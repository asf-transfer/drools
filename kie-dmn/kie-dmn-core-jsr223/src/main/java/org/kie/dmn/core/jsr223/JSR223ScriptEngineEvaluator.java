package org.kie.dmn.core.jsr223;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.kie.dmn.feel.runtime.FEELFunction;
import org.kie.dmn.feel.util.EvalHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JSR223ScriptEngineEvaluator {
    
    public static final String DMN_UNARYTEST_SYMBOL = "DMN_UNARYTEST_SYMBOL";
    private static final Logger LOG = LoggerFactory.getLogger( JSR223ScriptEngineEvaluator.class );
    private final ScriptEngine scriptEngine;
    private final String expression;
    
    public JSR223ScriptEngineEvaluator(ScriptEngine scriptEngine, String expression) {
        this.scriptEngine = scriptEngine;
        this.expression = expression;
    }
    
    /**
     * Opinionated evaluation for DMN scope.
     */
    public Object eval(Map<String, Object> ins) throws ScriptException {
        Bindings engineScope = createBindings(ins);
        Object result = scriptEngine.eval(expression, engineScope);
        LOG.info("Script result: {}", result);
        return EvalHelper.coerceNumber(result);
    }
    
    /**
     * Opinionated evaluation for DMN scope.
     */
    public boolean test(Object in, Map<String, Object> context) throws ScriptException {
        Bindings engineScope = createBindings(context);
        String keyForUnaryTest = Optional.ofNullable(scriptEngine.getFactory().getParameter(DMN_UNARYTEST_SYMBOL).toString()).orElse("_");
        engineScope.put(keyForUnaryTest, in);
        Object result = scriptEngine.eval(expression, engineScope);
        LOG.info("Script result: {}", result);
        return result == Boolean.TRUE ? true : false;
    }

    private Bindings createBindings(Map<String, Object> ins) {
        Bindings engineScope = scriptEngine.createBindings();
        Map<String, Object> _context = new HashMap<>();
        engineScope.put("_context", _context ); // an opinionated DMN choice.
        for (Entry<String, Object> kv : ins.entrySet()) { 
            String key = JSR223Utils.escapeIdentifierForBinding(kv.getKey());
            Object value = kv.getValue();
            // TODO should this be substituted with Jackson here?
            if (value instanceof BigDecimal) {
                value = JSR223Utils.doubleValueExact((BigDecimal) value);
            }
            if (value instanceof FEELFunction) {
                LOG.trace("SKIP binding {} of {}", key, value);
            } else {
                LOG.info("Setting binding {} to {}", key, value);
                engineScope.put(key, value);
                _context.put(kv.getKey(), value);
            }
        }
        return engineScope;
    }
}
