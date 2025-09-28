package app.baldphone.neo.views.popup;


public class Divider implements Action {

    @Override
    public ActionId id() {
        return ActionId.DIVIDER;
    }

    @Override
    public boolean enabled() {
        return false;
    }
}