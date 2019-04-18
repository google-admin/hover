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
import android.support.annotation.NonNull;
import android.support.v4.util.Pair;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;

/**
 * {@link HoverViewState} that operates the {@link HoverView} when it is closed. Closed means that
 * nothing is visible - no tabs, no content.  From the user's perspective, there is no
 * {@code HoverView}.
 */
class HoverViewStatePreviewed extends HoverViewStateCollapsed {

    private static final String TAG = "HoverViewStatePreviewed";
    private TabMessageView mMessageView;
    protected final Dragger.DragListener mMessageViewDragListener = new MessageViewDragListener(this);
    private boolean mCollapseOnDocked = false;

    HoverViewStatePreviewed() {
        init();
    }

    @Override
    public void takeControl(@NonNull HoverView hoverView, final Runnable onStateChanged) {
        super.takeControl(hoverView, null);
        Log.d(TAG, "Taking control.");
        mMessageView = mHoverView.mScreen.getTabMessageView(mHoverView.mSelectedSectionId);
        mMessageView.setMessageView(mSelectedSection.getTabMessageView());
        mMessageView.appear(mHoverView.mCollapsedDock, new Runnable() {
            @Override
            public void run() {
                if (!hasControl()) {
                    return;
                }
                onStateChanged.run();
                activateDragger();
            }
        });
    }

    @Override
    public void giveUpControl(@NonNull final HoverViewState nextState) {
        Log.d(TAG, "Giving up control.");
        if (nextState instanceof HoverViewStateCollapsed) {
            mMessageView.disappear(true);
        } else {
            mMessageView.disappear(false);
        }
        super.giveUpControl(nextState);
    }

    @Override
    protected void onPickedUpByUser() {
        mMessageView.disappear(true);
        mCollapseOnDocked = true;
        super.onPickedUpByUser();
    }

    @Override
    protected void onClose(final boolean userDropped) {
        super.onClose(userDropped);
        init();
    }

    @Override
    protected void activateDragger() {
        ArrayList<Pair<? extends HoverFrameLayout, ? extends BaseTouchController.TouchListener>> list = new ArrayList<>();
        list.add(new Pair<>(mFloatingTab, mFloatingTabDragListener));
        list.add(new Pair<>(mMessageView, mMessageViewDragListener));
        mHoverView.mDragger.activate(list);
    }

    @Override
    protected void onDocked() {
        super.onDocked();
        if (mCollapseOnDocked) {
            mHoverView.collapse();
            mCollapseOnDocked = false;
        }
    }

    @Override
    public HoverViewStateType getStateType() {
        return HoverViewStateType.PREVIEWED;
    }

    private void init() {
        mCollapseOnDocked = false;
    }

    private void hidePreview(final float startAlpha) {
        if (mHoverView == null) {
            return;
        }
        if (mMessageView != null) {
            mMessageView.disappear(false, startAlpha);
        }
        mHoverView.collapse();
    }

    private void notifyTabMessageViewOnTouchDown() {
        if (mHoverView == null) {
            return;
        }
        mHoverView.notifyMessageViewOnTouchDown();
    }

    private void notifyTabMessageViewOnTouchUp() {
        if (mHoverView == null) {
            return;
        }
        mHoverView.notifyMessageViewOnTouchUp();
    }

    protected static final class MessageViewDragListener implements Dragger.DragListener {

        private static final float ALPHA_THRESHOLD = 400;
        private static final float COLLAPSE_THRESHOLD = 300;
        private final HoverViewStatePreviewed mOwner;
        private float mOriginalX;
        private float mOriginalY;

        protected MessageViewDragListener(@NonNull HoverViewStatePreviewed owner) {
            mOwner = owner;
            init();
        }

        @Override
        public void onDragStart(View messageView, float x, float y) {
            if (messageView instanceof TabMessageView) {
                mOriginalX = messageView.getX() + messageView.getWidth() / 2;
                mOriginalY = messageView.getY() + messageView.getHeight() / 2;
                ((TabMessageView) messageView).moveCenterTo(new Point((int) x, (int) mOriginalY));
                updateAlpha(messageView, x);
                mOwner.setHoverMenuMode(HoverMenu.HoverMenuState.REMOVE_PREVIEW);
            }
        }

        @Override
        public void onDragTo(View messageView, float x, float y) {
            if (messageView instanceof TabMessageView) {
                ((TabMessageView) messageView).moveCenterTo(new Point((int) x, (int) mOriginalY));
                Log.d("ALPHA", "" + (Math.abs(x - mOriginalX) / 200));
                updateAlpha(messageView, x);
            }
        }

        @Override
        public void onReleasedAt(final View messageView, float x, float y) {
            if (messageView instanceof TabMessageView) {
                ((TabMessageView) messageView).moveCenterTo(new Point((int) mOriginalX, (int) mOriginalY));
                updateAlpha(messageView, mOriginalX);
                if (Math.abs(x - mOriginalX) > COLLAPSE_THRESHOLD) {
                    updateAlpha(messageView, mOriginalX);
                    mOwner.hidePreview(getAlpha(x));
                }
            }
            mOwner.setHoverMenuMode(HoverMenu.HoverMenuState.IDLE);
            init();
        }

        @Override
        public void onTap(View messageView) {
            mOwner.onTap();
        }

        @Override
        public void onTouchDown(View messageView) {
            mOwner.notifyTabMessageViewOnTouchDown();
        }

        @Override
        public void onTouchUp(View messageView) {
            mOwner.notifyTabMessageViewOnTouchUp();
        }

        private void init() {
            mOriginalX = 0;
            mOriginalY = 0;
        }

        private void updateAlpha(final View view, final float current) {
            view.setAlpha(getAlpha(current));
        }

        private float getAlpha(final float current) {
            return 1 - Math.max(0, Math.min(1, (Math.abs(current - mOriginalX) / ALPHA_THRESHOLD)));
        }
    }
}
