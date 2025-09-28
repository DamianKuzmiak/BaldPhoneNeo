package app.baldphone.neo.views.popup;

public interface ActionCallback {
    // Simple actions
    default void onEdit() {}

    default void onShare() {}

    default void onDelete() {}

    default void onFavoriteToggled(boolean nowFavorite) {}

    default void onHomeToggled(boolean nowPinned) {}
}
