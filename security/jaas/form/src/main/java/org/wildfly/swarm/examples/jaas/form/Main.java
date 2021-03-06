package org.wildfly.swarm.examples.jaas.form;

import java.util.HashMap;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.wildfly.swarm.Swarm;
import org.wildfly.swarm.config.security.Flag;
import org.wildfly.swarm.config.security.SecurityDomain;
import org.wildfly.swarm.config.security.security_domain.ClassicAuthentication;
import org.wildfly.swarm.config.security.security_domain.authentication.LoginModule;
import org.wildfly.swarm.datasources.DatasourcesFraction;
import org.wildfly.swarm.jpa.JPAFraction;
import org.wildfly.swarm.security.SecurityFraction;
import org.wildfly.swarm.undertow.WARArchive;
import org.wildfly.swarm.undertow.descriptors.WebXmlAsset;

public class Main {

    public static void main(String[] args) throws Exception {
        Swarm swarm = new Swarm();

        swarm.fraction(new DatasourcesFraction()
                       .dataSource("MyDS", (ds) -> {
                           ds.driverName("h2");
                           ds.connectionUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
                           ds.userName("sa");
                           ds.password("sa");
                       })
        );

        swarm.fraction(new JPAFraction()
                       .defaultDatasource("jboss/datasources/MyDS")
        );

        swarm.fraction(SecurityFraction.defaultSecurityFraction()
                       .securityDomain(new SecurityDomain("my-domain")
                           .classicAuthentication(new ClassicAuthentication()
                              .loginModule(new LoginModule("Database")
                                   .code("Database")
                                   .flag(Flag.REQUIRED).moduleOptions(new HashMap<Object, Object>() {{
                                      put("dsJndiName", "java:jboss/datasources/MyDS");
                                      put("principalsQuery", "SELECT password FROM REST_DB_ACCESS WHERE name=?");
                                      put("rolesQuery", "SELECT role, 'Roles' FROM REST_DB_ACCESS WHERE name=?");
                                  }})))));

        swarm.start();

        WARArchive deployment = ShrinkWrap.create(WARArchive.class);
        deployment.addPackage(Main.class.getPackage());

        deployment.addAsWebInfResource(new ClassLoaderAsset("META-INF/persistence.xml", Main.class.getClassLoader()), "classes/META-INF/persistence.xml");
        deployment.addAsWebInfResource(new ClassLoaderAsset("META-INF/load.sql", Main.class.getClassLoader()), "classes/META-INF/load.sql");

        deployment.addAsWebResource(new ClassLoaderAsset("login.jsp", Main.class.getClassLoader()), "login.jsp");
        deployment.addAsWebResource(new ClassLoaderAsset("login_error.jsp", Main.class.getClassLoader()), "login_error.jsp");

        deployment.addAsWebInfResource(new ClassLoaderAsset("WEB-INF/views/employees.jsp", Main.class.getClassLoader()), "views/employees.jsp");

        // Builder for web.xml and jboss-web.xml
        WebXmlAsset webXmlAsset = deployment.findWebXmlAsset();
        webXmlAsset.setFormLoginConfig("my-realm", "/login.jsp", "/login_error.jsp");
        webXmlAsset.protect("/*")
                .withMethod("GET", "POST")
                .withRole("admin");

        deployment.setSecurityDomain("my-domain");

        // Or, you can add web.xml and jboss-web.xml from classpath or somewhere
        // deployment.addAsWebInfResource(new ClassLoaderAsset("WEB-INF/web.xml", Main.class.getClassLoader()), "web.xml");
        // deployment.addAsWebInfResource(new ClassLoaderAsset("WEB-INF/jboss-web.xml", Main.class.getClassLoader()), "jboss-web.xml");

        swarm.deploy(deployment);
    }

}
