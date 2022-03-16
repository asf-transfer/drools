package org.kie.dmn.core.jsr223.jq;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;

public class JQScriptEngineFactory implements ScriptEngineFactory {

    @Override
    public String getEngineName() {
        return "Drools DMN Engine JQScriptEngineFactory";
    }

    @Override
    public String getEngineVersion() {
        return "alpha"; // TODO avoid Drools.getFullVersion() as that requires drools-core which is overkill here.
    }

    @Override
    public List<String> getExtensions() {
        return Collections.emptyList();
    }

    @Override
    public List<String> getMimeTypes() {
        return Arrays.asList("code/jq");
    }

    @Override
    public List<String> getNames() {
        return Arrays.asList("jq");
    }

    @Override
    public String getLanguageName() {
        return "jq";
    }

    @Override
    public String getLanguageVersion() {
        return "alpha"; // TODO align to consistency of dep version
    }

    @Override
    public Object getParameter(String key) {
        if (key.equals(ScriptEngine.NAME)) {
            return getLanguageName();
        } else if (key.equals(ScriptEngine.ENGINE)) {
            return getEngineName();
        } else if (key.equals(ScriptEngine.ENGINE_VERSION)) {
            return getEngineVersion();
        } else if (key.equals(ScriptEngine.LANGUAGE)) {
            return getLanguageName();
        } else if (key.equals(ScriptEngine.LANGUAGE_VERSION)) {
            return getLanguageVersion();
        } else if (key.equals(JQScriptEngine.DMN_UNARYTEST_SYMBOL)) {
            return JQScriptEngine.DMN_UNARYTEST_SYMBOL_VALUE;
        } else {
            return null;
        }
    }

    @Override
    public String getMethodCallSyntax(String obj, String m, String... args) {
        return null;
    }

    @Override
    public String getOutputStatement(String toDisplay) {
        return null;
    }

    @Override
    public String getProgram(String... statements) {
        return null;
    }

    @Override
    public ScriptEngine getScriptEngine() {
        return new JQScriptEngine(this);
    }

}
