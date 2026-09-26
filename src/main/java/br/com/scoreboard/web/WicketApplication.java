package br.com.scoreboard.web;

import org.apache.wicket.protocol.http.WebApplication;

public class WicketApplication extends WebApplication {
    @Override
    public Class<? extends org.apache.wicket.Page> getHomePage() {
        return HomePage.class;
    }

    @Override
    protected void init() {
        super.init();
        getCspSettings().blocking().disabled();
        mountPage("/HomePage.html", HomePage.class);
        mountPage("/wicket", HomePage.class);
    }
}