package com.github.sayedcsekuet.keycloak.Utils;

import com.github.sayedcsekuet.keycloak.policy.GroupPasswordPolicyProviderFactory;
import org.jboss.logging.Logger;
import org.keycloak.models.*;

import java.util.LinkedList;

public class PolicyCollector {
    private static final Logger logger = Logger.getLogger(PolicyCollector.class);

    public static String collectPolicies(RealmModel realm, UserModel user) {
        // First get the name of the attribute
        String groupPolicyName = realm.getPasswordPolicy().getPolicyConfig(GroupPasswordPolicyProviderFactory.ID);
        logger.infof("GroupPolicy group name %s", groupPolicyName);
        logger.infof("GroupPolicy user %s", user.getUsername());

        LinkedList<String> policies = new LinkedList<>();
        // Iterate groups and collect policy strings
        user.getGroups()
                .forEach((GroupModel group) -> {
                    if (group.getName().equals(groupPolicyName)) {
                        logger.debugf("GroupPolicy group %s", group.getName());
                        group.getAttributes().forEach((policyString, value) -> {
                            String policy = String.format("%s(%s)", policyString, value.stream().findFirst().map(Object::toString)
                                    .orElse(null));
                            logger.infof("GroupPolicy get policy: %s", policy);
                            policies.add(policy);
                        });
                    }
                });
        return String.join(" and ", policies);
    }

    public static PasswordPolicy parsePolicy(KeycloakSession session, String policy) {
        PasswordPolicy parsedPolicy = PasswordPolicy.parse(session, policy);
        return parsedPolicy;
    }
}
