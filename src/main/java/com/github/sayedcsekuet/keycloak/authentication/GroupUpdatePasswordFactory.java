package com.github.sayedcsekuet.keycloak.authentication;

import com.google.auto.service.AutoService;
import org.keycloak.Config;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.UserModel;

@AutoService(RequiredActionFactory.class)
public class GroupUpdatePasswordFactory implements RequiredActionFactory {
    private static final GroupUpdatePassword SINGLETON = new GroupUpdatePassword();

    @Override
    public void close() {

    }

    @Override
    public RequiredActionProvider create(KeycloakSession session) {
        return SINGLETON;
    }


    @Override
    public void init(Config.Scope config) {

    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public String getDisplayText() {
        return "Group Update Password";
    }


    @Override
    public String getId() {
        return UserModel.RequiredAction.UPDATE_PASSWORD.name();
    }

    @Override
    public boolean isOneTimeAction() {
        return true;
    }
}
