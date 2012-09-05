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

import javax.el.ELException;
import org.jboss.dmr.ModelNode;

/**
 * Exception used when a DMR operation returns a failed outcome.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2012 Red Hat Inc.
 */
public class DmrOperationFailedException extends ELException {

    private ModelNode operation;
    private ModelNode response;

    public DmrOperationFailedException(ModelNode operation, ModelNode response) {
        super(response.get("failure-description").asString());
        this.operation = operation;
        this.response = response;
    }

    public ModelNode getOperation() {
        return operation;
    }

    public ModelNode getResponse() {
        return response;
    }
}
