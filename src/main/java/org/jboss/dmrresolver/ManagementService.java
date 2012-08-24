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
package org.jboss.dmrresolver;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.server.Services;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistryException;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * This class comes from John Mazzitelli's blog post:
 * http://management-platform.blogspot.com/2012/07/co-located-management-client-for.html
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2012 Red Hat Inc.
 */
public class ManagementService implements ServiceActivator {
   private static volatile ModelController controller;
   private static volatile ExecutorService executor;


   public static ModelControllerClient getClient() {
      return controller.createClient(executor);
   }

   @Override
   public void activate(ServiceActivatorContext context) throws ServiceRegistryException {
      final GetModelControllerService service = new GetModelControllerService();
      context
          .getServiceTarget()
          .addService(ServiceName.of("management", "client", "getter"), service)
          .addDependency(Services.JBOSS_SERVER_CONTROLLER, ModelController.class, service.modelControllerValue)
          .install();
   }


   private class GetModelControllerService implements Service<Void> {
      private InjectedValue<ModelController> modelControllerValue = new InjectedValue<ModelController>();


      @Override
      public Void getValue() throws IllegalStateException, IllegalArgumentException {
         return null;
      }


      @Override
      public void start(StartContext context) throws StartException {
         ManagementService.executor = Executors.newFixedThreadPool(5, new ThreadFactory() {
             @Override
             public Thread newThread(Runnable r) {
                 Thread t = new Thread(r);
                 t.setDaemon(true);
                 t.setName("ManagementServiceModelControllerClientThread");
                 return t;
             }
         });
         ManagementService.controller = modelControllerValue.getValue();
      }

      @Override
      public void stop(StopContext context) {
         try {
            ManagementService.executor.shutdownNow();
         } finally {
            ManagementService.executor = null;
            ManagementService.controller = null;
         }
      }
   }
}