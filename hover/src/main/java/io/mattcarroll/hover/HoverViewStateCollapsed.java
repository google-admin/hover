/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.mattcarroll.hover;

import android.graphics.Point;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.util.ListUpdateCallback;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

/**
 * {@link HoverViewState} that operates the {@link HoverView} when it is collapsed. Collapsed means
 * that the only thing visible is the selected {@link FloatingTab}.  This tab docks itself against
 * the left or right sides of the screen.  The user can drag the tab around and drop it.
 * <p>
 * If the tab is tapped, the {@code HoverView} is transitioned to its expanded state.
 * <p>
 * If the tab is dropped on the exit region, the {@code HoverView} is transitioned to its closed state.
 */
class HoverViewStateCollapsed extends BaseHoverViewState {

    private static final String TAG = "HoverViewStateCollapsed";
    private static final float MIN_TAB_VERTICAL_POSITION = 0.0f;
    private static final float MAX_TAB_VERTICAL_POSITION = 1.0f;
    private static final long ALPHA_IDLE_MILLIS = 5000;
    private static final float ALPHA_IDLE_VALUE = 0.4f;

    protected FloatingTab mFloatingTab;
    protected final Dragger.DragListener mDragListener = new FloatingTabDragListener(this);
    protected HoverMenu.Section mSelectedSection;
    private int mSelectedSectionIndex = -1;
    private boolean mIsCollapsed = false;
    private Handler mHandler = new Handler();
    private Runnable mAlphaChanger = new Runnable() {
        @Override
        public void run() {
            final HoverViewState state = mHoverView.getState();
            if (!(state instanceof HoverViewStatePreviewed) && state instanceof HoverViewStateCollapsed) {
                if (mHoverView.shouldKeepVisible()) {
                    mFloatingTab.setAlpha(ALPHA_IDLE_VALUE);
                } else {
                    onClose(false);
                }
            }
        }
    };
    private Runnable mOnStateChanged;

    @Override
    public void takeControl(@NonNull HoverView hoverView, final Runnable onStateChanged) {
        super.takeControl(hoverView, onStateChanged);
        Log.d(TAG, "Taking control.");
        mOnStateChanged = onStateChanged;
        mHoverView.makeUntouchableInWindow();
        mHoverView.clearFocus(); // For handling hardware back button presses.

        Log.d(TAG, "Taking control with selected section: " + mHoverView.mSelectedSectionId);
        mSelectedSection = mHoverView.mMenu.getSection(mHoverView.mSelectedSectionId);
        mSelectedSection = null != mSelectedSection ? mSelectedSection : mHoverView.mMenu.getSection(0);
        mSelectedSectionIndex = mHoverView.mMenu.getSectionIndex(mSelectedSection);
        mFloatingTab = mHoverView.mScreen.getChainedTab(mHoverView.mSelectedSectionId);
        final boolean wasFloatingTabVisible;
        if (null == mFloatingTab) {
            wasFloatingTabVisible = false;
            mFloatingTab = mHoverView.mScreen.createChainedTab(mSelectedSection);
        } else {
            wasFloatingTabVisible = true;
        }
        mIsCollapsed = false; // We're collapsing, not yet collapsed.
        initDockPosition();

        // post() animation to dock in case the container hasn't measured itself yet.
        if (!wasFloatingTabVisible) {
            mFloatingTab.setVisibility(INVISIBLE);
        }
        mHoverView.post(new Runnable() {
            @Override
            public void run() {
                if (!hasControl()) {
                    return;
                }
                if (wasFloatingTabVisible) {
                    sendToDock();
                } else {
                    moveToDock();
                    mFloatingTab.appear(new Runnable() {
                        @Override
                        public void run() {
                            if (!hasControl()) {
                                return;
                            }
                            onDocked();
                        }
                    });
                }
            }
        });

        if (null != mHoverView.mMenu) {
            listenForMenuChanges();
        }

        scheduleHoverViewAlphaChange();
    }

    @Override
    public void giveUpControl(@NonNull HoverViewState nextState) {
        Log.d(TAG, "Giving up control.");
        restoreHoverViewAlphaValue();

        if (null != mHoverView.mMenu) {
            mHoverView.mMenu.setUpdatedCallback(null);
        }

        mHoverView.mScreen.getExitView().setVisibility(GONE);

        deactivateDragger();
        mFloatingTab = null;
        super.giveUpControl(nextState);
    }

    @Override
    public void setMenu(@Nullable final HoverMenu menu) {
        listenForMenuChanges();
    }

    private void listenForMenuChanges() {
        mHoverView.mMenu.setUpdatedCallback(new ListUpdateCallback() {
            @Override
            public void onInserted(int position, int count) {
                // no-op
            }

            @Override
            public void onRemoved(int position, int count) {
                Log.d(TAG, "onRemoved. Position: " + position + ", Count: " + count);
                if (mSelectedSectionIndex == position) {
                    Log.d(TAG, "Selected tab removed. Displaying a new tab.");
                    // TODO: externalize a selection strategy for when the selected section disappears
                    mHoverView.mScreen.destroyChainedTab(mFloatingTab);

                    mSelectedSectionIndex = mSelectedSectionIndex > 0 ? mSelectedSectionIndex - 1 : 0;
                    mSelectedSection = mHoverView.mMenu.getSection(mSelectedSectionIndex);
                    mHoverView.mSelectedSectionId = mSelectedSection.getId();
                    mFloatingTab = mHoverView.mScreen.createChainedTab(mSelectedSection);
                }
            }

            @Override
            public void onMoved(int fromPosition, int toPosition) {
                // no-op
            }

            @Override
            public void onChanged(int position, int count, Object payload) {
                Log.d(TAG, "Tab(s) changed. From: " + position + ", To: " + count);
                for (int i = position; i < position + count; ++i) {
                    if (i == mSelectedSectionIndex) {
                        Log.d(TAG, "Selected tab changed. Updating its display.");
                        mFloatingTab.setTabView(mHoverView.mMenu.getSection(position).getTabView());
                    }
                }
            }
        });
    }

    @Override
    public boolean respondsToBackButton() {
        return false;
    }

    @Override
    public void onBackPressed() {
        // No-op
    }

    protected void onPickedUpByUser() {
        mHoverView.mScreen.getExitView().setVisibility(VISIBLE);
        restoreHoverViewAlphaValue();
        mHoverView.notifyOnDragStart(this);
    }

    private void onDroppedByUser() {
        mHoverView.mScreen.getExitView().setVisibility(GONE);
        boolean droppedOnExit = mHoverView.mScreen.getExitView().isInExitZone(mFloatingTab.getPosition());
        if (droppedOnExit) {
            onClose(true);
        } else {
            int tabSize = mHoverView.getResources().getDimensionPixelSize(R.dimen.hover_tab_size);
            Point screenSize = mHoverView.getScreenSize();
            final float tabHorizontalPositionPercent = (float) mFloatingTab.getPosition().x / screenSize.x;
            final float viewHeightPercent = mFloatingTab.getHeight() / 2f / screenSize.y;
            float tabVerticalPosition = (float) mFloatingTab.getPosition().y / screenSize.y;
            if (tabVerticalPosition < MIN_TAB_VERTICAL_POSITION) {
                tabVerticalPosition = MIN_TAB_VERTICAL_POSITION;
            } else if (tabVerticalPosition > MAX_TAB_VERTICAL_POSITION - viewHeightPercent) {
                tabVerticalPosition = MAX_TAB_VERTICAL_POSITION - viewHeightPercent;
            }
            Log.d(TAG, "Dropped at horizontal " + tabHorizontalPositionPercent + ", vertical " + tabVerticalPosition);
            SideDock.SidePosition sidePosition = new SideDock.SidePosition(
                    tabHorizontalPositionPercent <= 0.5 ? SideDock.SidePosition.LEFT : SideDock.SidePosition.RIGHT,
                    tabVerticalPosition
            );
            mHoverView.mCollapsedDock = new SideDock(
                    mHoverView,
                    tabSize,
                    sidePosition
            );
            mHoverView.saveVisualState();
            Log.d(TAG, "User dropped tab. Sending to new dock: " + mHoverView.mCollapsedDock);

            sendToDock();
        }
    }

    protected void onClose(final boolean userDropped) {
        if (userDropped) {
            Log.d(TAG, "User dropped floating tab on exit.");
        } else {
            Log.d(TAG, "Auto dropped.");
        }
        mHoverView.close();
    }

    private void onTap() {
        Log.d(TAG, "Floating tab was tapped.");
        if (mHoverView != null) {
            mHoverView.notifyOnTap(this);
        }
    }

    private void sendToDock() {
        Log.d(TAG, "Sending floating tab to dock.");
        deactivateDragger();
        mFloatingTab.setDock(mHoverView.mCollapsedDock);
        mFloatingTab.dock(new Runnable() {
            @Override
            public void run() {
                if (!hasControl()) {
                    return;
                }
                onDocked();
            }
        });
    }

    private void moveToDock() {
        Log.d(TAG, "Moving floating tag to dock.");
        Point dockPosition = mHoverView.mCollapsedDock.sidePosition().calculateDockPosition(
                mHoverView.getScreenSize(),
                mFloatingTab.getTabSize()
        );
        mFloatingTab.moveTo(dockPosition);
    }

    private void initDockPosition() {
        if (null == mHoverView.mCollapsedDock) {
            int tabSize = mHoverView.getResources().getDimensionPixelSize(R.dimen.hover_tab_size);
            mHoverView.mCollapsedDock = new SideDock(
                    mHoverView,
                    tabSize,
                    new SideDock.SidePosition(SideDock.SidePosition.LEFT, 0.5f)
            );
        }
    }

    protected void onDocked() {
        Log.d(TAG, "Docked. Activating dragger.");
        if (!mHoverView.mIsAddedToWindow) {
            return;
        }
        activateDragger();
        scheduleHoverViewAlphaChange();

        // We consider ourselves having gone from "collapsing" to "collapsed" upon the very first dock.
        boolean didJustCollapse = !mIsCollapsed;
        mIsCollapsed = true;
        mHoverView.saveVisualState();
        if (didJustCollapse) {
            if (mOnStateChanged != null) {
                mOnStateChanged.run();
            }
        }
        mHoverView.notifyOnDocked(this);
    }

    protected void moveTabTo(View touchView, @NonNull Point position) {
        mFloatingTab.moveTo(position);
    }

    protected void activateDragger() {
        ArrayList<View> list = new ArrayList<>();
        list.add(mFloatingTab);
        mHoverView.mDragger.activate(mDragListener, list);
    }

    protected void deactivateDragger() {
        mHoverView.mDragger.deactivate();
    }

    private void scheduleHoverViewAlphaChange() {
        mHandler.postDelayed(mAlphaChanger, ALPHA_IDLE_MILLIS);
    }

    protected void restoreHoverViewAlphaValue() {
        mHandler.removeCallbacks(mAlphaChanger);
        mFloatingTab.setAlpha(1f);
    }

    @Override
    public HoverViewStateType getStateType() {
        return HoverViewStateType.COLLAPSED;
    }

    protected static final class FloatingTabDragListener implements Dragger.DragListener {

        private final HoverViewStateCollapsed mOwner;

        protected FloatingTabDragListener(@NonNull HoverViewStateCollapsed owner) {
            mOwner = owner;
        }

        @Override
        public void onDragStart(View touchView, float x, float y) {
            mOwner.onPickedUpByUser();
        }

        @Override
        public void onDragTo(View touchView, float x, float y) {
            mOwner.moveTabTo(touchView, new Point((int) x, (int) y));
        }

        @Override
        public void onReleasedAt(View touchView, float x, float y) {
            mOwner.onDroppedByUser();
        }

        @Override
        public void onPress() {
        }

        @Override
        public void onTap() {
            mOwner.onTap();
        }
    }
}
