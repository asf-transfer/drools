package org.drools.ruleunit.command.pmml;

import org.drools.core.command.impl.ContextImpl;
import org.drools.core.command.runtime.pmml.ApplyPmmlModelCommand;
import org.drools.core.impl.KnowledgeBaseFactory;
import org.junit.Test;
import org.kie.api.KieBase;
import org.kie.api.pmml.PMML4Result;
import org.kie.api.pmml.PMMLConstants;
import org.kie.api.pmml.PMMLRequestData;
import org.kie.api.runtime.Context;
import org.kie.internal.command.RegistryContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ApplyPmmlModelCommandTest {

    @Test(expected = IllegalStateException.class)
    public void testMissingRequestDataWithLegacy() {
        ApplyPmmlModelCommand cmd = new ApplyPmmlModelCommandTester(PMMLConstants.LEGACY);
        RegistryContext ctx = new ContextImpl();
        cmd.execute(ctx);
    }

    @Test
    public void testHasRequestDataAndKieBaseWithLegacy() {
        KieBase kbase = KnowledgeBaseFactory.newKnowledgeBase("defaultKieBase");
        ApplyPmmlModelCommand cmd = new ApplyPmmlModelCommandTester(PMMLConstants.LEGACY);
        PMMLRequestData data = new PMMLRequestData("123", "Sample Score");
        data.addRequestParam("age", 33.0);
        data.addRequestParam("occupation", "SKYDIVER");
        data.addRequestParam("residenceState", "KN");
        data.addRequestParam("validLicense", true);
        cmd.setRequestData(data);
        cmd.setPackageName("org.drools.scorecards.example");
        RegistryContext ctx = new ContextImpl().register(KieBase.class, kbase);
        PMML4Result resultHolder = cmd.execute(ctx);

        assertNotNull(resultHolder);
        String resultCode = resultHolder.getResultCode();
        // The empty KieBase doesn't have a rule unit associated with it
        assertEquals("ERROR-2", resultCode);
    }

    @Test
    public void testHasRequestDataWithLegacy() {
        ApplyPmmlModelCommand cmd = new ApplyPmmlModelCommandTester(PMMLConstants.LEGACY);
        PMMLRequestData data = new PMMLRequestData("123", "Sample Score");
        data.addRequestParam("age", 33.0);
        data.addRequestParam("occupation", "SKYDIVER");
        data.addRequestParam("residenceState", "KN");
        data.addRequestParam("validLicense", true);
        cmd.setRequestData(data);
        cmd.setPackageName("org.drools.scorecards.example");
        RegistryContext ctx = new ContextImpl();
        PMML4Result resultHolder = cmd.execute(ctx);

        assertNotNull(resultHolder);
        String resultCode = resultHolder.getResultCode();
        // Since we don't have a real KieBase we expect this error
        assertEquals("ERROR-1", resultCode);
    }

    @Test(expected = IllegalStateException.class)
    public void testMissingRequestDataWithTrusty() {
        ApplyPmmlModelCommand cmd = new ApplyPmmlModelCommandTester(PMMLConstants.NEW);
        RegistryContext ctx = new ContextImpl();
        cmd.execute(ctx);
    }

    @Test
    public void testHasRequestDataAndSourceWithNew() {
        ApplyPmmlModelCommand cmd = new ApplyPmmlModelCommandTester(PMMLConstants.NEW);
        PMMLRequestData data = new PMMLRequestData("123", "Sample Score");
        data.addRequestParam("age", 33.0);
        data.addRequestParam("occupation", "SKYDIVER");
        data.addRequestParam("residenceState", "KN");
        data.addRequestParam("validLicense", true);
        data.setSource("SOURCE");
        cmd.setRequestData(data);
        cmd.setPackageName("org.drools.scorecards.example");
        RegistryContext ctx = new ContextImpl();
        PMML4Result resultHolder = cmd.execute(ctx);

        assertNotNull(resultHolder);
        String resultCode = resultHolder.getResultCode();
        assertEquals("PMMLCommandExecutorTest", resultCode);
    }

    private class ApplyPmmlModelCommandTester extends ApplyPmmlModelCommand {

        private final PMMLConstants IMPLEMENTATION;

        public ApplyPmmlModelCommandTester(final PMMLConstants implementation) {
            super();
            IMPLEMENTATION = implementation;
        }

        @Override
        protected PMMLConstants getToInvoke(Context context) {
            return IMPLEMENTATION;
        }


    }


}
