package app.baldphone.neo.views.popup;

public final class MenuState {
    public final boolean isFavorite;
    public final boolean isPinnedToHome;

    public MenuState(boolean isFavorite, boolean isPinnedToHome) {
        this.isFavorite = isFavorite;
        this.isPinnedToHome = isPinnedToHome;
    }

    public MenuState withFavorite(boolean value) {
        return new MenuState(value, this.isPinnedToHome);
    }

    public MenuState withPinnedToHome(boolean value) {
        return new MenuState(this.isFavorite, value);
    }
}
