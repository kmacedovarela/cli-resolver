/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.test.cliresolver;

import java.util.List;
import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ELResolver;
import javax.el.ExpressionFactory;
import javax.el.FunctionMapper;
import javax.el.MethodExpression;
import javax.el.ValueExpression;
import javax.el.VariableMapper;
import javax.faces.FactoryFinder;
import javax.faces.application.Application;
import javax.faces.application.ApplicationFactory;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.cliresolver.DmrOperationFailedException;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Arquillian tests for the DMR Resolver.  These tests use a bare JSF application to make sure that a full
 * resolver chain is set up for testing.  Then we use the ExpressionFactory directly to create expressions
 * and test the resolver.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2012 Red Hat Inc.
 */
@RunWith(Arquillian.class)
public class ResolverTest {
    @Deployment
    public static WebArchive createDeployment() {
        return WarCreator.makeWar();
    }

    @Test
    public void basicTest() {
        // Test to see if I'm really calling the full resolver chain provided by JSF
        Integer value = (Integer)resolve("#{1 + 1}", Integer.class);
        Assert.assertTrue(value == 2);
    }

    @Test
    public void testRootAndDash() {
        String value = (String)resolve("#{_root_.launch_dash_type}", String.class);
        Assert.assertEquals("STANDALONE", value);
    }

    @Test
    public void testIsDomain() {
        Boolean value = (Boolean)resolve("#{_isDomain_}", Boolean.class);
        Assert.assertFalse(value);
    }

    @Test
    public void testBracketedAddress() {
        String value = (String)resolve("#{subsystem_eq_logging['console-handler_eq_CONSOLE'].level}", String.class);
        Assert.assertEquals("INFO", value);
    }

    @Test
    public void testSetStingValue() {
        Object value = resolve("#{subsystem_eq_logging.logger_eq_com_dot_arjuna.level}", String.class);
        Assert.assertEquals("WARN", value); // starts at WARN

        resolveAndSet("#{subsystem_eq_logging.logger_eq_com_dot_arjuna.level}", String.class, "INFO");

        value = resolve("#{subsystem_eq_logging.logger_eq_com_dot_arjuna.level}", String.class);
        Assert.assertEquals("INFO", value); // value changed to INFO
    }

    @Test
    public void testResolveValueTwice() {
        Object value = resolve("#{subsystem_eq_logging.logger_eq_sun_dot_rmi.level}", String.class);
        Assert.assertEquals("WARN", value); // starts at WARN
        value = resolve("#{subsystem_eq_logging.logger_eq_sun_dot_rmi.level}", String.class);
        Assert.assertEquals("WARN", value); // stays at WARN
    }

    @Test
    public void testSetIntegerValue() {
        Object value = resolve("#{subsystem_eq_web.configuration_eq_jsp_dash_configuration.modification_dash_test_dash_interval}", Integer.class);
        Assert.assertEquals(4, value); // starts at 4

        resolveAndSet("#{subsystem_eq_web.configuration_eq_jsp_dash_configuration.modification_dash_test_dash_interval}", Integer.class, 5);

        value = resolve("#{subsystem_eq_web.configuration_eq_jsp_dash_configuration.modification_dash_test_dash_interval}", Integer.class);
        Assert.assertEquals(5, value); // value changed to 5
    }

    @Test
    public void testSetBooleanValue() {
        Object value = resolve("#{subsystem_eq_web.configuration_eq_jsp_dash_configuration.development}", Boolean.class);
        Assert.assertEquals(false, value); // starts at false

        resolveAndSet("#{subsystem_eq_web.configuration_eq_jsp_dash_configuration.development}", Boolean.class, true);

        value = resolve("#{subsystem_eq_web.configuration_eq_jsp_dash_configuration.development}", Boolean.class);
        Assert.assertEquals(true, value); // value changed to true
    }

    @Test(expected = DmrOperationFailedException.class)
    public void testSetReadOnlyValue() {
        Object value = resolve("#{subsystem_eq_web.connector_eq_http.maxTime}", Integer.class);
        Assert.assertEquals(0, value); // starts at zero

        resolveAndSet("#{subsystem_eq_web.connector_eq_http.maxTime}", Integer.class, 999);
    }

    @Test
    public void testCLICommandResultingInList() {
        Object value = resolve("#{_CLI_['/subsystem=logging/logger=*/:read-resource']}", List.class);
        Assert.assertNotNull(value);
        List<ModelNode> list = (List<ModelNode>)value;

        Assert.assertEquals(5, list.size()); // 5 loggers defined in standalone-test.xml

        boolean found = false;
        for (ModelNode logger : list) {
            String level = logger.get("result", "level").asString();
            String loggerName = logger.get("address").asPropertyList().get(1).getValue().asString(); // name is second element in address
            if (loggerName.equals("jacorb")) {
                found = true;
                Assert.assertEquals("WARN", level);
            }
        }

        Assert.assertTrue("Logger jacorb was not found in the resut", found);
    }

    @Test
    public void testCLICommandResultingInModelNode() {
        ModelNode value = (ModelNode)resolve("#{_CLI_['/:read-attribute(name=launch-type)']}", ModelNode.class);
        Assert.assertNotNull(value);
        Assert.assertEquals("STANDALONE", value.asString());
    }

    @Test
    public void testCLICommandResultingInFailure() {
        // lunch-type :-)
        try {
            resolve("#{_CLI_['/:read-attribute(name=lunch-type)']}", ModelNode.class);
            Assert.fail("Expected ELException");
        } catch (ELException e) {
            Assert.assertTrue(e.getMessage().contains("JBAS014792: Unknown attribute lunch-type"));
        }
    }

    @Test
    public void testResolutionOfElementInPropertyList() {
        // CLI command returns a list of loggers (List<ModelNode>).  Then resolve the first element in the list (a ModelNode),
        // followed by the address attribute, which is a property list.  Finally, resolve the value of the subsystem property.
        String value = (String)resolve("#{_CLI_['/subsystem=logging/logger=*/:read-resource'][0].address.subsystem}", String.class);
        Assert.assertNotNull(value);
        Assert.assertEquals("logging", value);
    }

    @Test
    public void testResolutionOfOrdinaryList() {
        String el = "#{core_dash_service_eq_platform_dash_mbean.type_eq_garbage_dash_collector.name_eq_PS_MarkSweep.memory_dash_pool_dash_names}";
        List<String> list = (List<String>)resolve(el, List.class);
        Object element = list.get(0);
        Assert.assertTrue("List is of wrong type", element instanceof String);
    }

    @Test
    public void testAsPropertyList() {
        String el = "#{_CLI_['/core-service=management/:read-resource(recursive=true)']._asPropertyList_}";
        List<Property> propList = (List<Property>)resolve(el, List.class);
        Assert.assertEquals(3, propList.size());
        for (Property prop: propList) {
            Assert.assertTrue(prop instanceof Property);
        }
    }

    @Test
    public void testMethodExpression() {
        String el = "#{subsystem_eq_logging.periodic_dash_rotating_dash_file_dash_handler_eq_FILE.encoding}";
        String encoding = (String)resolve(el, String.class);
        //Assert.assertNull(encoding);

        String methodExp = "#{_CLI_['/subsystem=logging/periodic-rotating-file-handler=FILE/:write-attribute(name=encoding,value=UTF-8)'].get('outcome')}";
        ModelNode result = (ModelNode)resolveMethodExp(methodExp, null, new Class[0]);
        Assert.assertEquals("success", result.asString());

        encoding = (String)resolve(el, String.class);
        Assert.assertEquals("UTF-8", encoding);
    }

    // resolve value expression and set value
    private void resolveAndSet(String expression, Class<?> expectedType, Object valueToSet) {
        ELContext ctx = new TestELContext();
        ValueExpression exp = expFactory().createValueExpression(ctx, expression, expectedType);
        exp.setValue(ctx, valueToSet);
    }

    // resolve value expression
    private Object resolve(String expression, Class<?> expectedType) {
        ELContext ctx = new TestELContext();
        ValueExpression exp = expFactory().createValueExpression(ctx, expression, expectedType);
        return exp.getValue(ctx);
    }

    // resolve method expression
    private Object resolveMethodExp(String expression, Class<?> expectedReturnType, Class<?>[] expectedParamTypes, Object... params) {
        ELContext ctx = new TestELContext();
        MethodExpression exp = expFactory().createMethodExpression(ctx, expression, expectedReturnType, expectedParamTypes);
        return exp.invoke(ctx, params);
    }

    private Application application() {
        ApplicationFactory factory = (ApplicationFactory) FactoryFinder.getFactory(FactoryFinder.APPLICATION_FACTORY);
        return factory.getApplication();
    }

    private ExpressionFactory expFactory() {
        return application().getExpressionFactory();
    }

    private class TestELContext extends ELContext {
        @Override
        public ELResolver getELResolver() {
            return application().getELResolver();
        }

        @Override
        public FunctionMapper getFunctionMapper() {
            return null;
        }

        @Override
        public VariableMapper getVariableMapper() {
            return null;
        }
    }
}
