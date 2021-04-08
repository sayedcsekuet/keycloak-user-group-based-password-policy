ARG DOCKER_VERSION=12.0.4
FROM jboss/keycloak:${DOCKER_VERSION}
COPY target/keycloak-user-group-based-password-policy-1.0.0.jar /opt/jboss/keycloak/standalone/deployments
RUN /opt/jboss/keycloak/bin/add-user-keycloak.sh -u admin -p admin
#RUN /opt/jboss/keycloak/
