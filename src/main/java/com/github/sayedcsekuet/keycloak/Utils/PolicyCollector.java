package com.github.sayedcsekuet.keycloak.Utils;

import com.github.sayedcsekuet.keycloak.policy.GroupPasswordPolicyProviderFactory;
import org.jboss.logging.Logger;
import org.keycloak.models.*;

import java.util.HashMap;

public class PolicyCollector {
    private static final Logger logger = Logger.getLogger(PolicyCollector.class);

    public static String collectPolicies(RealmModel realm, UserModel user) {
        // We are using hash map for replacing duplicate
        HashMap<String, String> policies = new HashMap<>();
        // First get the name of the attribute
        String groupPolicyName = realm.getPasswordPolicy().getPolicyConfig(GroupPasswordPolicyProviderFactory.ID);
        if (null == groupPolicyName) {
            return "";
        }
        logger.infof("GroupPolicy policy group names %s", groupPolicyName);
        String[] groups = groupPolicyName.split(",");
        for (String groupName : groups) {
            logger.infof("GroupPolicy group name %s", groupName);
            logger.infof("GroupPolicy user %s", user.getUsername());
            // Iterate groups and collect policy strings
            user.getGroups()
                    .forEach((GroupModel group) -> {
                        if (group.getName().trim().equals(groupName.trim())) {
                            logger.infof("GroupPolicy group %s", group.getName());
                            group.getAttributes().forEach((policyString, value) -> {
                                policyString = policyString.trim();
                                String policy = String.format("%s(%s)", policyString, value.stream().findFirst().map(Object::toString)
                                        .orElse(null));
                                logger.infof("GroupPolicy get policy: %s", policy);
                                policies.put(policyString, policy);
                            });
                        }
                    });
        }
        return String.join(" and ", policies.values());
    }

    public static PasswordPolicy parsePolicy(KeycloakSession session, String policy) {
        PasswordPolicy parsedPolicy = PasswordPolicy.parse(session, policy);
        return parsedPolicy;
    }
}
