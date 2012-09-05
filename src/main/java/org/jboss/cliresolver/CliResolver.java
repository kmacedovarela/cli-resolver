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
package org.jboss.cliresolver;

import java.beans.FeatureDescriptor;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ELResolver;
import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

/**
 * This EL resolver knows how to get/set values from the AS7 management model.
 * See documentation for the full EL syntax used to invoke this resolver.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2012 Red Hat Inc.
 */
public class CliResolver extends ELResolver {

    public static final String ROOT = "_root_";
    public static final String IS_DOMAIN = "_isDomain_";
    public static final String CLI = "_CLI_";
    public static final String AS_PROP_LIST = "_asPropertyList_";

    private static final boolean isDomain;
    private static final ModelControllerClient localDmrClient = ManagementService.getClient();
    private static ModelControllerClient domainDmrClient = null;

    // cliContext only used to convert CLI command strings to ModelNode.
    // This should not connect to any server
    private static final CommandContext cliContext;

    static {
        ModelNode readLaunchType = new ModelNode();
        readLaunchType.get("address").setEmptyList();
        readLaunchType.get("operation").set("read-attribute");
        readLaunchType.get("name").set("launch-type");

        try {
            ModelNode result = localDmrClient.execute(readLaunchType);
            isDomain = !result.get("result").asString().equals("STANDALONE");

            if (isDomain) {
                InetAddress domainAddress = InetAddress.getByName(System.getProperty("jboss.domain.master.address"));
                int domainPort = Integer.parseInt(System.getProperty("jboss.domain.master.port", "9999"));
                domainDmrClient = ModelControllerClient.Factory.create(domainAddress, domainPort);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        try {
            cliContext = CommandContextFactory.getInstance().newCommandContext();
        } catch (CliInitializationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Create a new CliResolver.
     */
    public CliResolver() {
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext elCtx, Object base) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext elCtx, Object base) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Class<?> getType(ELContext elCtx, Object base, Object property) {
        String strProperty = property.toString();
        strProperty = replaceCharsNotAllowedInEL(strProperty.trim());

        if (strProperty.equals(IS_DOMAIN)) {
            elCtx.setPropertyResolved(true);
            return Boolean.class;
        }

        if (strProperty.equals(CLI)) {
            elCtx.setPropertyResolved(true);
            return String.class;
        }

        if (strProperty.equals(AS_PROP_LIST)) {
            elCtx.setPropertyResolved(true);
            return List.class;
        }

        if (isPropertyList(strProperty)) {
            elCtx.setPropertyResolved(true);
            return ModelNode.class;
        }

        if (base == null) {
            return null;
        }

        if (base.equals(CLI)) {
            elCtx.setPropertyResolved(true);
            return ModelNode.class;
        }

        if (!(base instanceof ModelNode)) {
            return null;
        }

        // I can handle this
        elCtx.setPropertyResolved(true);

        ModelNode operation = ((ModelNode) base).clone();
        operation.get("operation").set("read-resource-description");

        try {
            ModelNode result = dmrClient().execute(operation);
            ModelType type = result.get("result", "attributes", strProperty, "type").asType();
            return convertToJavaType(type);
        } catch (IOException e) {
            throw new ELException(e);
        }
    }

    @Override
    public Object getValue(ELContext elCtx, Object base, Object property) {
        if (property.equals(IS_DOMAIN)) {
            elCtx.setPropertyResolved(true);
            return isDomain;
        }

        if (property.equals(CLI)) {
            elCtx.setPropertyResolved(true);
            // return CLI as the base
            return CLI;
        }

        if (property.equals(AS_PROP_LIST)) {
            elCtx.setPropertyResolved(true);
            ModelNode node = (ModelNode)base;
            return node.asPropertyList();
        }

        boolean isPropertyList = isPropertyList(base);

        if ((base != null)
                && !(base instanceof ModelNode)
                && !isPropertyList
                && !base.equals(CLI)) {
            return null;
        }

        // Let other resolvers handle List<ModelNode>
        if (!isPropertyList && (base instanceof ModelNode)) {
            ModelNode node = (ModelNode) base; // <-- I think this might be wrong. If base is a ModelNode we have to handle it?
            if (node.getType() == ModelType.LIST) {
                return null;
            }
        }

        String strProperty = (String) property;
        strProperty = replaceCharsNotAllowedInEL(strProperty.trim());

        boolean propIsAddress = isAddress(strProperty);
        if ((base == null) && (!propIsAddress)) {
            return null;
        }

        // I'll handle this
        elCtx.setPropertyResolved(true);

        if (base == null) { // property must be an address
            ModelNode operation = new ModelNode();
            if (strProperty.equals(ROOT)) {
                operation.get("address").setEmptyList();
                return operation;
            }

            return addAddress(operation, strProperty);
        }

        if (isPropertyList) { // base is List<Property>
            List<Property> propList = ((ModelNode) base).asPropertyList();
            for (Property prop : propList) {
                if (prop.getName().equals(property)) {
                    return convertValueToJavaType(prop.getValue());
                }
            }

            return null; // property not found
        }

        if (base.equals(CLI)) {
            return executeCLI(strProperty);
        }

        // base must be a ModelNode
        ModelNode modelNode = (ModelNode) base;

        if (propIsAddress) {
            return addAddress(modelNode, strProperty);
        }

        // POTENTIAL BUG: if strProperty is "address" this could return the address path
        // we've been building.  Use a different name for our address path until we are ready to execute the modelNode?
        if (modelNode.hasDefined(strProperty)) { // property is an attribute of the current base
            ModelNode value = modelNode.get(strProperty);

            // We handle property lists ourselves but allow another resolver
            // to handle an ordinary List<ModelNode>
            if (isPropertyList(value)) {
                return value;
            }

            return convertValueToJavaType(value);
        }

        // property must be an attribute at the address specified in the ModelNode
        // Get the value from the management model
        modelNode = modelNode.clone();
        modelNode.get("operation").set("read-attribute");
        modelNode.get("name").set(strProperty);

        Object finalResult = null;
        try {
            ModelNode result = dmrClient().execute(modelNode);
            finalResult = convertValueToJavaType(result.get("result"));

            if (!(finalResult instanceof List)) {
                return finalResult;
            } else {
                return convertListElements(modelNode.get("address"), (List<ModelNode>) finalResult, strProperty);
            }
        } catch (IOException e) {
            throw new ELException(e);
        }
    }

    @Override
    public boolean isReadOnly(ELContext elCtx, Object base, Object property) {
        if (base == null) {
            return false;
        }
        if (!(base instanceof ModelNode)) {
            return false;
        }

        // I'll handle this
        elCtx.setPropertyResolved(true);

        ModelNode operation = ((ModelNode) base).clone();
        operation.get("operation").set("read-resource-description");

        try {
            ModelNode result = dmrClient().execute(operation);
            String accessType = result.get("result", "attributes", (String) property, "access-type").asString();
            return !accessType.equals("read-write");
        } catch (IOException e) {
            throw new ELException(e);
        }
    }

    @Override
    public void setValue(ELContext elCtx, Object base, Object property, Object value) {
        if (base == null) {
            return;
        }
        if (!(base instanceof ModelNode)) {
            return;
        }

        // I'll handle this
        elCtx.setPropertyResolved(true);

        String strProperty = (String) property;
        strProperty = replaceCharsNotAllowedInEL(strProperty.trim());

        ModelNode operation = (ModelNode) base;
        operation = operation.clone();

        operation.get("name").set(strProperty);

        Class type = getType(elCtx, base, property);
        if ((value == null) || (type == null)) {
            operation.get("operation").set("undefine-attribute");
        } else {
            operation.get("operation").set("write-attribute");
            setValueAttribute(operation, type, value);
        }

        try {
            ModelNode result = dmrClient().execute(operation);
            if (isOutcomeFailed(result)) {
                throw new DmrOperationFailedException(operation, result);
            }
        } catch (IOException e) {
            throw new ELException(e);
        }
    }

    private boolean isOutcomeFailed(ModelNode result) {
        String outcome = result.get("outcome").asString();
        return !outcome.equals("success");
    }

    private boolean isPropertyList(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof ModelNode)) {
            return false;
        }
        ModelNode node = (ModelNode) obj;
        if (node.getType() != ModelType.LIST) {
            return false;
        }
        List<ModelNode> list = node.asList();
        if (list.isEmpty()) {
            return false;
        }
        for (ModelNode element : list) { // every element must be a property
            if (element.getType() != ModelType.PROPERTY) {
                return false;
            }
        }

        return true;
    }

    private void setValueAttribute(ModelNode operation, Class type, Object value) {
        ModelNode valueNode = operation.get("value");
        if (type == null) {
            return;  // value will be set to undefined
        }
        if (type.isAssignableFrom(ModelNode.class)) {
            valueNode.set((ModelNode) value);
        }
        if (type.isAssignableFrom(BigDecimal.class)) {
            valueNode.set((BigDecimal) value);
        }
        if (type.isAssignableFrom(BigInteger.class)) {
            valueNode.set((BigInteger) value);
        }
        if (type.isAssignableFrom(Boolean.class)) {
            valueNode.set((Boolean) value);
        }
        if (type.isAssignableFrom(byte[].class)) {
            valueNode.set((byte[]) value);
        }
        if (type.isAssignableFrom(Double.class)) {
            valueNode.set((Double) value);
        }
        if (type.isAssignableFrom(Integer.class)) {
            valueNode.set((Integer) value);
        }
        if (type.isAssignableFrom(Collection.class)) {
            valueNode.set((Collection) value);
        }
        if (type.isAssignableFrom(Long.class)) {
            valueNode.set((Long) value);
        }
        if (type.isAssignableFrom(Object.class)) {
            valueNode.set((ModelNode) value);
        }
        if (type.isAssignableFrom(Property.class)) {
            valueNode.set((Property) value);
        }
        valueNode.set(value.toString()); // default to String
    }

    private Class<?> convertToJavaType(ModelType type) {
        if (type == ModelType.BIG_DECIMAL) {
            return BigDecimal.class;
        }
        if (type == ModelType.BIG_INTEGER) {
            return BigInteger.class;
        }
        if (type == ModelType.BOOLEAN) {
            return Boolean.class;
        }
        if (type == ModelType.BYTES) {
            return byte[].class;
        }
        if (type == ModelType.DOUBLE) {
            return Double.class;
        }
        if (type == ModelType.INT) {
            return Integer.class;
        }
        if (type == ModelType.LIST) {
            return Collection.class;
        }
        if (type == ModelType.LONG) {
            return Long.class;
        }
        if (type == ModelType.OBJECT) {
            return Object.class;
        }
        if (type == ModelType.PROPERTY) {
            return Property.class;
        }
        if (type == ModelType.STRING) {
            return String.class;
        }
        return null;
    }

    private Object convertValueToJavaType(ModelNode node) {
        if (!node.isDefined()) {
            return null;
        }

        return convertValueToJavaType(node, node.getType());
    }

    private Object convertValueToJavaType(ModelNode node, ModelType type) {
        if (!node.isDefined()) {
            return null;
        }

        if (type == ModelType.BIG_DECIMAL) {
            return node.asBigDecimal();
        }
        if (type == ModelType.BIG_INTEGER) {
            return node.asBigInteger();
        }
        if (type == ModelType.BOOLEAN) {
            return node.asBoolean();
        }
        if (type == ModelType.BYTES) {
            return node.asBytes();
        }
        if (type == ModelType.DOUBLE) {
            return node.asDouble();
        }
        if (type == ModelType.INT) {
            return node.asInt();
        }
        if (type == ModelType.LIST) {
            return node.asList();
        }
        if (type == ModelType.LONG) {
            return node.asLong();
        }
        if (type == ModelType.OBJECT) {
            return node.asObject();
        }
        if (type == ModelType.PROPERTY) {
            return node.asProperty();
        }
        if (type == ModelType.STRING) {
            return node.asString();
        }
        if (type == ModelType.TYPE) {
            return node.asType();
        }
        return null;
    }

    // Convert the elements in a List<ModelNode> to some List<Class<?>> based on the resource description
    private List convertListElements(ModelNode address, List<ModelNode> elements, String property) throws IOException {
        // find the list type we need
        ModelNode rscDesc = new ModelNode();
        rscDesc.get("address").set(address.clone());
        rscDesc.get("operation").set("read-resource-description");
        ModelNode result = dmrClient().execute(rscDesc);
        ModelType type = result.get("result", "attributes", property, "value-type").asType();

        // create a new list with the elements converted
        List convertedList = new ArrayList(elements.size());
        for (ModelNode element : elements) {
            convertedList.add(convertValueToJavaType(element, type));
        }

        return convertedList;
    }

    private String replaceCharsNotAllowedInEL(String str) {
        str = str.replace("_dash_", "-");
        str = str.replace("_dot_", ".");
        str = str.replace("_astk_", "*");
        return str;
    }

    // add the address to the modelNode
    private ModelNode addAddress(ModelNode operation, String property) {
        ModelNode op = operation.clone();
        ModelNode address = op.get("address");
        address.add(makeAddress(property));
        return op;
    }

    private ModelNode makeAddress(String property) {
        ModelNode address = new ModelNode();
        String lvalue = property.substring(0, property.indexOf("_eq_"));
        String rvalue = property.substring(property.indexOf("_eq_") + 4);
        address.get(lvalue).set(rvalue);
        return address;
    }

    private boolean isAddress(String property) {
        return property.contains("_eq_") || property.equals(ROOT);
    }

    // return the correct DMR client depending on if we are running standalone or domain
    private ModelControllerClient dmrClient() {
        if (isDomain) {
            return domainDmrClient;
        }
        return localDmrClient;
    }

    private Object executeCLI(String command) {
        ModelNode operation = null;
        try {
            operation = cliContext.buildRequest(command);
        } catch (CommandFormatException e) {
            throw new ELException(e);
        }

        try {
            ModelNode result = dmrClient().execute(operation);
            if (isOutcomeFailed(result)) {
                throw new DmrOperationFailedException(operation, result);
            }

            ModelNode commandResult = result.get("result");
            if (!commandResult.isDefined()) return result;

            if (commandResult.getType() == ModelType.LIST) {
                return commandResult.asList();
            } else {
                return commandResult;
            }
        } catch (IOException e) {
            throw new ELException(e);
        }
    }
}
