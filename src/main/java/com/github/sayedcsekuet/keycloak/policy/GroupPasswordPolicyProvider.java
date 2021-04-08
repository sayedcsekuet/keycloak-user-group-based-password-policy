package com.github.sayedcsekuet.keycloak.policy;

import com.github.sayedcsekuet.keycloak.Utils.PolicyCollector;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.policy.PasswordPolicyConfigException;

public class GroupPasswordPolicyProvider extends PolicyProviderMultiplexer {

    private static final Logger logger = Logger.getLogger(GroupPasswordPolicyProvider.class);

    public GroupPasswordPolicyProvider(KeycloakSession session) {
        super(session);
    }

    @Override
    protected String findPolicies(RealmModel realm, UserModel user) {
        String policy = PolicyCollector.collectPolicies(realm, user);
        logger.infof("GroupPolicy adding group policy: %s", policy);
        return policy;
    }

    @Override
    public Object parseConfig(String value) {
        if (value == null || value.isEmpty()) {
            throw new PasswordPolicyConfigException("GroupPolicy Attribute name cannot be blank");
        }
        return value;
    }

    @Override
    public void close() {
    }
}
