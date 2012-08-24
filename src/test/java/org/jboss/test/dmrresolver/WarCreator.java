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
package org.jboss.test.dmrresolver;

import java.io.File;
import org.jboss.dmrresolver.DmrResolver;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;

/**
 * Create WAR in a different class from the test class.  That way, we don't need things like MavenDependencyResolver
 * in the WAR.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2012 Red Hat Inc.
 */
public class WarCreator {

    private WarCreator() {
    }

//    private static final MavenDependencyResolver resolver = DependencyResolvers.use(MavenDependencyResolver.class)
//                                                                               .loadMetadataFromPom("pom.xml")
//                                                                               .goOffline();

    public static WebArchive makeWar() {
        //String version = System.getProperty("projectVersion");
        WebArchive war = ShrinkWrap.create(WebArchive.class)
                                   .as(WebArchive.class)
                                   .addAsWebInfResource(new File("src/test/resources/faces-config.xml"))
                                   .addAsWebInfResource(EmptyAsset.INSTANCE, ArchivePaths.create("beans.xml"))

                                // This didn't work because it would always install the jar from the last successful test run
                                // instead of the latest code.  But if it did work we wouldn't need makeResolverJar().  Sigh.
                                // .addAsLibraries(resolver.artifact("org.jboss.dmrresolver:dmrresolver:" + version).resolveAsFiles());

                                   .addAsLibraries(makeResolverJar());
   //     System.out.println("war =");
   //     System.out.println(war.toString(true));
        return war;
    }

    // Make the final jar by hand.  We have to do this because the test will run before the package phase.  The true jar
    // doesn't exist yet.
    private static JavaArchive makeResolverJar() {
        JavaArchive jar =  ShrinkWrap.create(JavaArchive.class)
                                     .as(JavaArchive.class)
                                     .addPackage(DmrResolver.class.getPackage())
                                     .addAsManifestResource(new File("src/test/shrinkwrap/MANIFEST.MF"))
                                     .addAsManifestResource(new File("src/main/resources/META-INF/faces-config.xml"))
                                     .addAsManifestResource(new File("src/main/resources/META-INF/services/org.jboss.msc.service.ServiceActivator"), "services/org.jboss.msc.service.ServiceActivator");
  //      System.out.println("DRMResolver jar=");
  //      System.out.println(jar.toString(true));
        return jar;
    }
}
