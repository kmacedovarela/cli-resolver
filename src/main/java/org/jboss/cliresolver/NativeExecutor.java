/*
 * JBoss, Home of Professional Open Source
 * Copyright 2013 Red Hat Inc. and/or its affiliates and other contributors
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

import java.io.IOException;
import java.util.Locale;
import javax.el.ELException;
import javax.faces.context.FacesContext;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2013 Red Hat Inc.
 */
public class NativeExecutor {

    private static CommandContext cliContext = CliResolver.cliContext();

    private String command;


    public NativeExecutor(String command) {
        this.command = command;
    }

    /**
     * This gets called for MethodExpressions.
     *
     */
    public void execute() {
        executeCLIAndHandleResult(command);
    }

    /**
     * This gets called for ValueExpressions.
     *
     * @return The result of the command.
     */
    public Object getExecute() {
        return executeCLIAndHandleResult(command);
    }

    private Object executeCLIAndHandleResult(String command) {
        try {
            ModelNode result = executeCLI(command);

            if (CliResolver.isOutcomeFailed(result)) {
                throw new DmrOperationFailedException(command, result);
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
        } catch (CommandFormatException e) {
            throw new ELException(e);
        }
    }

    public static ModelNode executeCLI(String command) throws IOException, CommandFormatException {
        ModelNode operation = cliContext.buildRequest(command);
        return executeOperation(operation);
    }

    public static ModelNode executeOperation(ModelNode operation) throws IOException {
        addLocale(operation);
        return CliResolver.dmrClient().execute(operation);
    }

    // TODO: find a way to test this with Arquillian
    private static void addLocale(ModelNode operation) {
        FacesContext fctx = FacesContext.getCurrentInstance();
        if (fctx == null) return;

        Locale locale = fctx.getApplication().getViewHandler().calculateLocale(fctx);
        operation.get("locale").set(locale.toString());
    }
}
