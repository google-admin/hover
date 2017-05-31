package io.mattcarroll.hover;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import io.mattcarroll.hover.view.InViewGroupDragger;
import io.mattcarroll.hover.window.InWindowDragger;
import io.mattcarroll.hover.window.WindowViewController;

/**
 * {@code HoverMenuView} is a floating menu implementation. This implementation displays tabs along
 * the top of its display, from right to left. Below the tabs, filling the remainder of the display
 * is a content region that displays the content for a selected tab.  The content region includes
 * a visual indicator showing which tab is currently selected.  Each tab's content includes a title
 * and a visual area.  The visual area can display any {@code View}.
 *
 * {@code HoverMenuView} cannot be used in XML because it requires additional parameters in its
 * constructor.
 */
public class HoverMenuView extends RelativeLayout {

    private static final String TAG = "HoverMenuView";

    private static final String SAVED_STATE_DOCK_POSITION = "dock_position";
    private static final String SAVED_STATE_DOCKS_SIDE = "dock_side";
    private static final String SAVED_STATE_SELECTED_SECTION = "selected_section";

    @NonNull
    public static HoverMenuView createForWindow(@NonNull Context context,
                                                @Nullable SharedPreferences savedInstanceState,
                                                @NonNull WindowViewController windowViewController) {
        int touchDiameter = context.getResources().getDimensionPixelSize(R.dimen.exit_radius);
        int slop = ViewConfiguration.get(context).getScaledTouchSlop();
        InWindowDragger dragger = new InWindowDragger(
                context,
                windowViewController,
                touchDiameter,
                slop
        );

        return new HoverMenuView(context, dragger, savedInstanceState, windowViewController);
    }

    @NonNull
    public static HoverMenuView createForView(@NonNull Context context,
                                              @Nullable SharedPreferences savedInstanceState,
                                              @NonNull ViewGroup container) {
        int touchDiameter = context.getResources().getDimensionPixelSize(R.dimen.exit_radius);
        int slop = ViewConfiguration.get(context).getScaledTouchSlop();
        InViewGroupDragger dragger = new InViewGroupDragger(
                container,
                touchDiameter,
                slop
        );

        return new HoverMenuView(context, dragger, savedInstanceState, null);
    }

    private final WindowViewController mWindowViewController;
    private final Dragger mDragger;
    private final Screen mScreen;
    private HoverMenuViewStateCollapsed mCollapsedMenu;
    private SideDock mCollapsedDock;
    private HoverMenuViewStateExpanded mExpandedMenu;
    private HoverMenu.SectionId mSelectedSectionId;
    private HoverMenu mMenu;
    private boolean mIsAddedToWindow;
    private boolean mIsExpanded = false;
    private ExitListener mExitListener;
    private boolean mIsDebugMode = false;

    private HoverMenuView(@NonNull Context context,
                          @NonNull Dragger dragger,
                          @Nullable SharedPreferences savedInstanceState,
                          @Nullable WindowViewController windowViewController) {
        super(context);
        mDragger = dragger;
        mScreen = new Screen(this);
        mWindowViewController = windowViewController;

        if (null != savedInstanceState) {
            restoreStateFromBundle(savedInstanceState);
        }
    }

    public void release() {
        Log.d(TAG, "Released.");
        mDragger.deactivate();
        // TODO: should we also release the screen?
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                removeOnLayoutChangeListener(this);
                initAfterInitialLayout();
            }
        });
    }

    private void initAfterInitialLayout() {
        // Start collapsed.
        if (null != mWindowViewController) {
            mWindowViewController.makeUntouchable(this);
        }
        if (null == mSelectedSectionId) {
            mSelectedSectionId = mMenu.getSection(0).getId();
        }
        createCollapsedMenu();
        mCollapsedMenu.takeControl(mScreen, mSelectedSectionId.toString());
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        if (null != mCollapsedMenu) {
            mCollapsedDock = mCollapsedMenu.getDock();
        }
        Log.d(TAG, "onSaveInstanceState(). Dock: " + mCollapsedDock
                + ", Selected section: " + mSelectedSectionId);
        Parcelable superState = super.onSaveInstanceState();
        SavedState savedState = new SavedState(superState);
        savedState.setCollapsedDock(mCollapsedDock);
        savedState.setSelectedSectionId(mSelectedSectionId);
        return savedState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        mCollapsedDock = savedState.getCollapsedDock();
        HoverMenu.SectionId savedSelectedSectionId = savedState.getSelectedSectionId();
        Log.d(TAG, "onRestoreInstanceState(). Dock: " + mCollapsedDock
                + ", Selected section: " + savedSelectedSectionId);

        // If no menu is set on this HoverMenuView then we should hold onto this saved section
        // selection in case we get a menu that has this section.  If we do have a menu set on
        // this HoverMenuView, then we should only restore this selection if the given section
        // exists in our menu.
        if (null == mMenu || (null != savedSelectedSectionId && null != mMenu.getSection(savedSelectedSectionId))) {
            mSelectedSectionId = savedSelectedSectionId;
        }
    }

    public void saveStateToBundle(@NonNull SharedPreferences.Editor editor) {
        if (null != mCollapsedMenu) {
            mCollapsedDock = mCollapsedMenu.getDock();
        }
        editor.putFloat(SAVED_STATE_DOCK_POSITION, mCollapsedDock.getVerticalDockPositionPercentage());
        editor.putInt(SAVED_STATE_DOCKS_SIDE, mCollapsedDock.getSide());
        editor.putString(SAVED_STATE_SELECTED_SECTION, null != mSelectedSectionId ? mSelectedSectionId.toString() : null);
        editor.commit();

        Log.d(TAG, "saveStateToBundle(). Position: "
                + mCollapsedDock.getVerticalDockPositionPercentage()
                + ", Side: " + mCollapsedDock.getSide()
                + ", Section ID: " + mSelectedSectionId);
    }

    private void restoreStateFromBundle(@NonNull SharedPreferences prefs) {
        mCollapsedDock = new SideDock(
                prefs.getFloat(SAVED_STATE_DOCK_POSITION, 0.5f),
                prefs.getInt(SAVED_STATE_DOCKS_SIDE, SideDock.LEFT)
        );
        mSelectedSectionId = prefs.contains(SAVED_STATE_SELECTED_SECTION) ?
                new HoverMenu.SectionId(prefs.getString(SAVED_STATE_SELECTED_SECTION, null)) :
                null;

        Log.d(TAG, "restoreStateFromBundle(). Position: "
                + mCollapsedDock.getVerticalDockPositionPercentage()
                + ", Side: " + mCollapsedDock.getSide()
                + ", Section ID: " + mSelectedSectionId);
    }

    public void enableDebugMode(boolean debugMode) {
        mIsDebugMode = debugMode;

        mDragger.enableDebugMode(debugMode);
        mScreen.enableDrugMode(debugMode);
    }

    public void setExitListener(@Nullable ExitListener listener) {
        mExitListener = listener;
    }

    public void setMenu(@Nullable HoverMenu menu) {
        mMenu = menu;

        if (null == mSelectedSectionId || null == mMenu.getSection(mSelectedSectionId)) {
            mSelectedSectionId = mMenu.getSection(0).getId();
        }
    }

    private void expand() {
        if (!mIsExpanded) {
            mIsExpanded = true;
            if (null != mWindowViewController) {
                mWindowViewController.makeTouchable(this);
            }

            createExpandedMenu();

            mCollapsedDock = mCollapsedMenu.getDock();
            mCollapsedMenu.giveControlTo(mExpandedMenu);
            mCollapsedMenu = null;
        }
    }

    private void createExpandedMenu() {
        Log.d(TAG, "Creating expanded menu. Selected section: " + mSelectedSectionId);
        mExpandedMenu = new HoverMenuViewStateExpanded(mSelectedSectionId);
        mExpandedMenu.setListener(new HoverMenuViewStateExpanded.Listener() {
            @Override
            public void onExpanding() { }

            @Override
            public void onExpanded() {
                mScreen.getContentDisplay().setVisibility(VISIBLE);
            }

            @Override
            public void onCollapseRequested() {
                collapse();
            }
        });
        mExpandedMenu.setMenu(mMenu);
    }

    private void collapse() {
        if (mIsExpanded) {
            mIsExpanded = false;
            if (null != mWindowViewController) {
                mWindowViewController.makeUntouchable(this);
            }

            createCollapsedMenu();

            mSelectedSectionId = mExpandedMenu.getActiveSectionId();
            mExpandedMenu.giveControlTo(mCollapsedMenu);
            mExpandedMenu = null;

            mScreen.getContentDisplay().setVisibility(GONE);
        }
    }

    private void createCollapsedMenu() {
        Log.d(TAG, "Creating collapsed menu. Dock: " + mCollapsedDock);
        mCollapsedMenu = new HoverMenuViewStateCollapsed(mDragger, mCollapsedDock);
        mCollapsedMenu.setMenu(mMenu);
        mCollapsedMenu.setListener(new HoverMenuViewStateCollapsed.Listener() {
            @Override
            public void onDragStart() {
                mScreen.getExitView().setVisibility(VISIBLE);
            }

            @Override
            public void onDragEnd() {
                mScreen.getExitView().setVisibility(GONE);
            }

            @Override
            public void onDocked() {
                Log.d(TAG, "Floating tab has docked.");
                mCollapsedDock = mCollapsedMenu.getDock();
            }

            @Override
            public void onTap() {
                Log.d(TAG, "Expanding the hover menu.");
                expand();
            }

            @Override
            public void onDroppedOnExit() {
                Log.d(TAG, "Floating tab dropped on exit.");
                if (null != mExitListener) {
                    mExitListener.onExit();
                }
            }
        });
    }

    // Only call this if using HoverMenuView directly in a window.
    public void addToWindow() {
        if (!mIsAddedToWindow) {
            mWindowViewController.addView(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    false,
                    this
            );

            mIsAddedToWindow = true;
        }
    }

    // Only call this if using HoverMenuView directly in a window.
    public void removeFromWindow() {
        if (null != mWindowViewController) {
            mWindowViewController.removeView(this);
        }
    }

    private static class SavedState extends BaseSavedState {

        private static final Parcelable.Creator<SavedState> CREATOR = new Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel source) {
                return new SavedState(source);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };

        private SideDock mCollapsedDock;
        private HoverMenu.SectionId mSelectedSectionId;

        SavedState(Parcelable superState) {
            super(superState);
        }

        SavedState(Parcel in) {
            super(in);

            if (in.dataAvail() > 0) {
                float dockVerticalPositionPercent = in.readFloat();
                int side = in.readInt();
                mCollapsedDock = new SideDock(dockVerticalPositionPercent, side);
            }
            if (in.dataAvail() > 0) {
                mSelectedSectionId = new HoverMenu.SectionId(in.readString());
            }
        }

        @Nullable
        public SideDock getCollapsedDock() {
            return mCollapsedDock;
        }

        public void setCollapsedDock(@Nullable SideDock collapsedDock) {
            mCollapsedDock = collapsedDock;
        }

        @Nullable
        public HoverMenu.SectionId getSelectedSectionId() {
            return mSelectedSectionId;
        }

        public void setSelectedSectionId(@Nullable HoverMenu.SectionId selectedSectionId) {
            mSelectedSectionId = selectedSectionId;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);

            if (null != mCollapsedDock) {
                out.writeFloat(mCollapsedDock.getVerticalDockPositionPercentage());
                out.writeInt(mCollapsedDock.getSide());
            }
        }
    }
}