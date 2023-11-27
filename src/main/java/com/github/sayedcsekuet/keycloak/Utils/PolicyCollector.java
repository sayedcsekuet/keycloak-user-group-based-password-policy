package com.github.sayedcsekuet.keycloak.Utils;

import com.github.sayedcsekuet.keycloak.policy.GroupPasswordPolicyProviderFactory;
import org.jboss.logging.Logger;
import org.keycloak.models.*;
import org.keycloak.policy.PasswordPolicyProvider;

import java.util.HashMap;

public class PolicyCollector {
    private static final Logger logger = Logger.getLogger(PolicyCollector.class);

    public static String collectPolicies(KeycloakSession session, RealmModel realm, UserModel user) {
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
            user.getGroupsStream()
                    .forEach((GroupModel group) -> {
                        if (group.getName().trim().equals(groupName.trim())) {
                            group.getAttributes().forEach((policyString, value) -> {
                                policyString = policyString.trim();
                                PasswordPolicyProvider provider = session.getProvider(PasswordPolicyProvider.class, policyString);
                                if (provider != null && GroupPasswordPolicyProviderFactory.ID != policyString) {
                                    String policy = String.format("%s(%s)", policyString, value.stream().findFirst().map(Object::toString)
                                            .orElse(null));
                                    logger.infof("GroupPolicy get policy: %s", policy);
                                    policies.put(policyString, policy);
                                }
                            });
                        }
                    });
        }
        return String.join(" and ", policies.values());
    }

    public static PasswordPolicy parsePolicy(KeycloakSession session, String policy) {
        return PasswordPolicy.parse(session, policy);
    }

    public static PasswordPolicy createGroupPolicy(KeycloakSession session, RealmModel realm, UserModel user) {
        String policyString = PolicyCollector.collectPolicies(session, realm, user);
        logger.infof("GroupPolicy collected policy: %s", policyString);
        return PolicyCollector.parsePolicy(session, policyString);
    }

    public static PasswordPolicy mergeGroupPolicy(KeycloakSession session, RealmModel realm, UserModel user) {
        String groupPolicy = PolicyCollector.collectPolicies(session, realm, user);
        if (groupPolicy.isEmpty()) {
            return realm.getPasswordPolicy();
        }
        String mergedPolicy = realm.getPasswordPolicy().toString() + " and " + groupPolicy;
        logger.infof("Merged Policy string: %s", mergedPolicy);
        return PolicyCollector.parsePolicy(session, mergedPolicy);
    }
}
