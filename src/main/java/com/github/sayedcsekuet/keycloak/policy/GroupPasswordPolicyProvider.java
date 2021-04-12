/*
 * Copyright 2019 Julian Picht
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.sayedcsekuet.keycloak.policy;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;

import com.github.sayedcsekuet.keycloak.Utils.PolicyCollector;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.PasswordPolicy;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.policy.PasswordPolicyConfigException;
import org.keycloak.policy.PasswordPolicyProvider;
import org.keycloak.policy.PolicyError;
import org.keycloak.theme.Theme;

public class GroupPasswordPolicyProvider implements PasswordPolicyProvider {

    protected KeycloakSession session;

    public GroupPasswordPolicyProvider(KeycloakSession session) {
        this.session = session;
    }


    private static final Logger logger = Logger.getLogger(GroupPasswordPolicyProvider.class);

    @Override
    public Object parseConfig(String value) {
        if (value == null || value.isEmpty()) {
            throw new PasswordPolicyConfigException("GroupPolicy group name cannot be blank");
        }
        return value;
    }

    @Override
    public PolicyError validate(RealmModel realm, UserModel user, String password) {
        String policyString = PolicyCollector.collectPolicies(session, realm, user);
        logger.infof("GroupPolicy Adding group policy %s", policyString);
        PasswordPolicy policy = PolicyCollector.parsePolicy(session, policyString);
        LinkedList<PolicyError> list = new LinkedList<>(validatePasswordPolicy(policy, realm, user, password));

        if (list.isEmpty()) {
            return null;
        }

        return translateMessges(list, user);
    }

    protected LinkedList<PolicyError> validatePasswordPolicy(PasswordPolicy policy, RealmModel realm, UserModel user, String password) {
        LinkedList<PolicyError> list = new LinkedList<>();

        for (String id : policy.getPolicies()) {
            PolicyError error = validatePolicyProvider(policy, id, realm, user, password);
            if (null != error) {
                list.add(error);
            }
        }
        return list;
    }

    protected PolicyError validatePolicyProvider(PasswordPolicy policy, String id, RealmModel realm, UserModel user, String password) {
        RealmModel realRealm = session.getContext().getRealm();
        try {
            Realm fakeRealm = new Realm();
            fakeRealm.setPasswordPolicy(policy);

            session.getContext().setRealm(fakeRealm);

            PasswordPolicyProvider provider = session.getProvider(PasswordPolicyProvider.class, id);
            return provider.validate(realm, user, password);
        } finally {
            session.getContext().setRealm(realRealm);
        }
    }

    // Translate the messages and remove the common prefix.
    // We wan't to return ONE message with ALL the problems, not one problem at a time.
    // The messages have common prefixes in most languages.
    protected PolicyError translateMessges(LinkedList<PolicyError> list, UserModel user) {
        Properties messageProps;
        try {
            messageProps = session.theme().getTheme(Theme.Type.ACCOUNT).getMessages(session.getContext().resolveLocale(user));
        } catch (IOException e) {
            return new PolicyError(e.getLocalizedMessage());
        }
        PrefixRemover messages = new PrefixRemover();

        for (PolicyError e : list) {
            messages.add(MessageFormat.format(messageProps.getProperty(e.getMessage(), e.getMessage()), e.getParameters()));
        }
        return new PolicyError(messages.getPrefix() + String.join(System.lineSeparator(), messages.getMessagesWithoutPrefix()));
    }

    @Override
    public void close() {
    }

    @Override
    public PolicyError validate(String username, String password) {
        return null;
    }

    private class PrefixRemover {
        public ArrayList<String> messages;
        public String prefix;

        PrefixRemover() {
            messages = new ArrayList<>();
            prefix = null;
        }

        void add(String str) {
            messages.add(str);

            // handle first element
            if (prefix == null) {
                prefix = str;
                return;
            }

            // if the current prefix is a prefix to the new message no changes are needed.
            if (str.startsWith(prefix)) {
                return;
            }

            List<String> strParts = Arrays.asList(str.split(" "));
            List<String> prefixParts = Arrays.asList(prefix.split(" "));

            int minLength = Math.min(strParts.size(), prefixParts.size());
            for (int i = 0; i < minLength; i++) {
                if (!strParts.get(i).equals(prefixParts.get(i))) {
                    prefix = String.join(" ", prefixParts.subList(0, i));
                    break;
                }
            }
        }

        public String getPrefix() {
            return prefix;
        }

        public LinkedList<String> getMessagesWithoutPrefix() {
            LinkedList<String> out = new LinkedList<>();
            for (String msg : messages) {
                out.add(msg.substring(prefix.length()));
            }
            return out;
        }
    }
}
