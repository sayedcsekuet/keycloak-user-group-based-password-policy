package com.github.sayedcsekuet.keycloak.authentication;

import com.github.sayedcsekuet.keycloak.Utils.PolicyCollector;
import com.github.sayedcsekuet.keycloak.policy.Realm;
import com.google.auto.service.AutoService;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.common.util.Resteasy;
import org.keycloak.common.util.ResteasyProvider;
import org.keycloak.common.util.Time;
import org.keycloak.credential.CredentialModel;
import org.keycloak.credential.CredentialProvider;
import org.keycloak.credential.PasswordCredentialProvider;
import org.keycloak.credential.PasswordCredentialProviderFactory;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.events.EventType;
import org.keycloak.models.*;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.jboss.resteasy.spi.HttpRequest;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@AutoService(RequiredActionFactory.class)
public class GroupUpdatePassword implements RequiredActionProvider, RequiredActionFactory {
    private static final Logger logger = Logger.getLogger(GroupUpdatePasswordFactory.class);
    private ResteasyProvider resteasyProvider;

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
        Realm fakeRealm = new Realm();
        fakeRealm.setPasswordPolicy(PolicyCollector.createGroupPolicy(context.getSession(), context.getRealm(), context.getUser()));
        int daysToExpirePassword = fakeRealm.getPasswordPolicy().getDaysToExpirePassword();
        logger.infof("GroupPolicy expires day %d", daysToExpirePassword);
        if (daysToExpirePassword != -1) {
            PasswordCredentialProvider passwordProvider = (PasswordCredentialProvider) context.getSession().getProvider(CredentialProvider.class, PasswordCredentialProviderFactory.PROVIDER_ID);
            CredentialModel password = passwordProvider.getPassword(context.getRealm(), context.getUser());
            if (password != null) {
                if (password.getCreatedDate() == null) {
                    context.getUser().addRequiredAction(UserModel.RequiredAction.UPDATE_PASSWORD);
                    logger.info("GroupPolicy User is required to update password");
                } else {
                    long timeElapsed = Time.toMillis(Time.currentTime()) - password.getCreatedDate();
                    long timeToExpire = TimeUnit.DAYS.toMillis(daysToExpirePassword);

                    if (timeElapsed > timeToExpire) {
                        context.getUser().addRequiredAction(UserModel.RequiredAction.UPDATE_PASSWORD);
                        logger.info("GroupPolicy User is required to update password");
                    }
                }
            }
        }
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        Response challenge = context.form()
                .setAttribute("username", context.getAuthenticationSession().getAuthenticatedUser().getUsername())
                .createResponse(UserModel.RequiredAction.UPDATE_PASSWORD);
        context.challenge(challenge);
    }

    @Override
    public void processAction(RequiredActionContext context) {
        EventBuilder event = context.getEvent();
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        RealmModel realm = context.getRealm();
        UserModel user = context.getUser();
        KeycloakSession session = context.getSession();
        MultivaluedMap<String, String> formData = resteasyProvider.getContextData(HttpRequest.class).getDecodedFormParameters();
        event.event(EventType.UPDATE_PASSWORD);
        String passwordNew = formData.getFirst("password-new");
        String passwordConfirm = formData.getFirst("password-confirm");

        EventBuilder errorEvent = event.clone().event(EventType.UPDATE_PASSWORD_ERROR)
                .client(authSession.getClient())
                .user(authSession.getAuthenticatedUser());

        if (Validation.isBlank(passwordNew)) {
            Response challenge = context.form()
                    .setAttribute("username", authSession.getAuthenticatedUser().getUsername())
                    .addError(new FormMessage(Validation.FIELD_PASSWORD, Messages.MISSING_PASSWORD))
                    .createResponse(UserModel.RequiredAction.UPDATE_PASSWORD);
            context.challenge(challenge);
            errorEvent.error(Errors.PASSWORD_MISSING);
            return;
        } else if (!passwordNew.equals(passwordConfirm)) {
            Response challenge = context.form()
                    .setAttribute("username", authSession.getAuthenticatedUser().getUsername())
                    .addError(new FormMessage(Validation.FIELD_PASSWORD_CONFIRM, Messages.NOTMATCH_PASSWORD))
                    .createResponse(UserModel.RequiredAction.UPDATE_PASSWORD);
            context.challenge(challenge);
            errorEvent.error(Errors.PASSWORD_CONFIRM_ERROR);
            return;
        }

        if (getId().equals(authSession.getClientNote(Constants.KC_ACTION_EXECUTING))
                && "on".equals(formData.getFirst("logout-sessions"))) {
            session.sessions().getUserSessionsStream(realm, user)
                    .filter(s -> !Objects.equals(s.getId(), authSession.getParentSession().getId()))
                    .collect(Collectors.toList()) // collect to avoid concurrent modification as back channelLogout removes the user sessions.
                    .forEach(s -> AuthenticationManager.backchannelLogout(session, realm, s, session.getContext().getUri(),
                            context.getConnection(), context.getHttpRequest().getHttpHeaders(), true));
        }

        PasswordPolicy originalPolicy = realm.getPasswordPolicy();

        try {
            PasswordPolicy mergedPolicy = PolicyCollector.mergeGroupPolicy(context.getSession(), context.getRealm(), context.getUser());
            realm.setPasswordPolicy(mergedPolicy);
            user.credentialManager().updateCredential(UserCredentialModel.password(passwordNew, false));
            context.success();
        } catch (ModelException me) {
            errorEvent.detail(Details.REASON, me.getMessage()).error(Errors.PASSWORD_REJECTED);
            Response challenge = context.form()
                    .setAttribute("username", authSession.getAuthenticatedUser().getUsername())
                    .setError(me.getMessage(), me.getParameters())
                    .createResponse(UserModel.RequiredAction.UPDATE_PASSWORD);
            context.challenge(challenge);
        } catch (Exception ape) {
            errorEvent.detail(Details.REASON, ape.getMessage()).error(Errors.PASSWORD_REJECTED);
            Response challenge = context.form()
                    .setAttribute("username", authSession.getAuthenticatedUser().getUsername())
                    .setError(ape.getMessage())
                    .createResponse(UserModel.RequiredAction.UPDATE_PASSWORD);
            context.challenge(challenge);
        } finally {
            realm.setPasswordPolicy(originalPolicy);
        }
    }

    @Override
    public RequiredActionProvider create(KeycloakSession session) {
        setResteasyProvider(Resteasy.getProvider());
        return this;
    }

    @Override
    public void init(Config.Scope scope) {

    }

    @Override
    public void postInit(KeycloakSessionFactory keycloakSessionFactory) {

    }

    @Override
    public void close() {

    }

    @Override
    public String getId() {
        return UserModel.RequiredAction.UPDATE_PASSWORD.name();
    }

    @Override
    public boolean isOneTimeAction() {
        return true;
    }

    @Override
    public String getDisplayText() {
        return "Update Password";
    }

    void setResteasyProvider(ResteasyProvider resteasyProvider) {
        this.resteasyProvider = resteasyProvider;
    }
}
