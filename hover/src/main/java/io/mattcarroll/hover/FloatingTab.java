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

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

/**
 * {@code FloatingTab} is the cornerstone of a {@link HoverView}.  When a {@code HoverView} is
 * collapsed, it is reduced to a single {@code FloatingTab} that the user can drag and drop.  When
 * a {@code HoverView} is expanded, that one {@code FloatingTab} slides to a row of tabs that appear
 * and offer a menu system.
 * <p>
 * A {@code FloatingTab} can move around the screen in various ways. A {@code FloatingTab} can place
 * itself at a "dock position", or slide from its current position to its "dock position", or
 * position itself at an arbitrary location on screen.
 * <p>
 * {@code FloatingTab}s position themselves based on their center.
 */
class FloatingTab extends HoverFrameLayout {

    private static final String TAG = "FloatingTab";
    private static final int APPEARING_ANIMATION_DURATION = 300;

    private final String mId;
    private int mTabSize;
    private View mTabView;
    private Dock mDock;
    private AnimatorSet mAnimatorSetDisappear;
    private AnimatorSet mAnimatorSetAppear;

    public FloatingTab(@NonNull Context context, @NonNull String tabId) {
        super(context);
        mId = tabId;
        mTabSize = getResources().getDimensionPixelSize(R.dimen.hover_tab_size);
        setClipChildren(false);
        setClipToPadding(false);

        int padding = getResources().getDimensionPixelSize(R.dimen.hover_tab_margin);
        setPadding(padding, padding, padding, padding);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateSize();
    }

    private void updateSize() {
        // Make this View the desired size.
        final ViewGroup.LayoutParams layoutParams = getLayoutParams();
        layoutParams.width = mTabSize;
        layoutParams.height = mTabSize;
        setLayoutParams(layoutParams);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mDock != null) {
            moveCenterTo(mDock.position());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    public void enableDebugMode(boolean debugMode) {
        if (debugMode) {
            setBackgroundColor(0x8800FF00);
        } else {
            setBackgroundColor(Color.TRANSPARENT);
        }
    }

    public void appear(@Nullable final Runnable onAppeared) {
        cancelAnimatorSetAppearIfNeeded();
        mAnimatorSetAppear = new AnimatorSet();
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(this, "scaleX", 0.0f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(this, "scaleY", 0.0f, 1.0f);
        mAnimatorSetAppear.setDuration(APPEARING_ANIMATION_DURATION);
        mAnimatorSetAppear.setInterpolator(new OvershootInterpolator());
        mAnimatorSetAppear.playTogether(scaleX, scaleY);
        mAnimatorSetAppear.start();

        mAnimatorSetAppear.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (null != onAppeared) {
                    onAppeared.run();
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });

        setVisibility(VISIBLE);
    }

    public void appearImmediate() {
        cancelAnimatorSetDisappearIfNeeded();
        setVisibility(VISIBLE);
        setScaleX(1.0f);
        setScaleY(1.0f);
    }

    public void disappear(@Nullable final Runnable onDisappeared) {
        cancelAnimatorSetDisappearIfNeeded();
        mAnimatorSetDisappear = new AnimatorSet();
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(this, "scaleX", 0.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(this, "scaleY", 0.0f);
        mAnimatorSetDisappear.setDuration(APPEARING_ANIMATION_DURATION);
        mAnimatorSetDisappear.playTogether(scaleX, scaleY);
        mAnimatorSetDisappear.start();

        mAnimatorSetDisappear.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                setVisibility(GONE);

                if (null != onDisappeared) {
                    onDisappeared.run();
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });
    }

    public void disappearImmediate() {
        cancelAnimatorSetAppearIfNeeded();
        setVisibility(GONE);
    }

    private void cancelAnimatorSetAppearIfNeeded() {
        if (mAnimatorSetAppear != null && mAnimatorSetAppear.isRunning()) {
            mAnimatorSetAppear.cancel();
            mAnimatorSetAppear = null;
        }
    }

    private void cancelAnimatorSetDisappearIfNeeded() {
        if (mAnimatorSetDisappear != null && mAnimatorSetDisappear.isRunning()) {
            mAnimatorSetDisappear.cancel();
            mAnimatorSetDisappear = null;
        }
    }

    public void shrink() {
        mTabSize = getResources().getDimensionPixelSize(R.dimen.hover_tab_size_shrunk);
        updateSize();
        setPadding(0, 0, 0, 0);
    }

    public void expand() {
        mTabSize = getResources().getDimensionPixelSize(R.dimen.hover_tab_size);
        updateSize();
        int padding = getResources().getDimensionPixelSize(R.dimen.hover_tab_margin);
        setPadding(padding, padding, padding, padding);
    }

    @NonNull
    public String getTabId() {
        return mId;
    }

    public int getTabSize() {
        return mTabSize;
    }

    public void setTabView(@Nullable View view) {
        if (view == mTabView) {
            // If Tab View hasn't changed, no need to do anything.
            return;
        }

        removeAllViews();

        mTabView = view;
        if (null != mTabView) {
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            addView(mTabView, layoutParams);
        }
    }

    // Returns the center position of this tab.
    @NonNull
    public Point getPosition() {
        return new Point(
                (int) (getX() + (getTabSize() / 2)),
                (int) (getY() + (getTabSize() / 2))
        );
    }

    @Nullable
    public Point getDockPosition() {
        return mDock.position();
    }

    public void setDock(@NonNull Dock dock) {
        mDock = dock;
        notifyListenersOfDockChange();
    }

    public void dock() {
        dock(null);
    }

    public void dock(@Nullable final Runnable onDocked) {
        Point destinationCornerPosition = convertCenterToCorner(mDock.position());
        Log.d(TAG, "Docking to destination point: " + destinationCornerPosition);

        ObjectAnimator xAnimation = ObjectAnimator.ofFloat(this, "x", destinationCornerPosition.x);
        xAnimation.setDuration(500);
        xAnimation.setInterpolator(new OvershootInterpolator());
        ObjectAnimator yAnimation = ObjectAnimator.ofFloat(this, "y", destinationCornerPosition.y);
        yAnimation.setDuration(500);
        yAnimation.setInterpolator(new OvershootInterpolator());
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(xAnimation).with(yAnimation);
        animatorSet.start();

        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (null != onDocked) {
                    onDocked.run();
                }
                notifyListenersOfPositionChange(FloatingTab.this);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });

        xAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                notifyListenersOfPositionChange(FloatingTab.this);
            }
        });
    }

    public void closeAnimation(Point targetPosition, @Nullable final Runnable onDocked) {
        Point destinationCornerPosition = convertCenterToCorner(targetPosition);
        Log.d(TAG, "Docking to destination point: " + destinationCornerPosition);

        ObjectAnimator xAnimation = ObjectAnimator.ofFloat(this, "x", targetPosition.x);
        xAnimation.setDuration(500);
        xAnimation.setInterpolator(new OvershootInterpolator());
        ObjectAnimator yAnimation = ObjectAnimator.ofFloat(this, "y", targetPosition.y);
        yAnimation.setDuration(500);
        yAnimation.setInterpolator(new OvershootInterpolator());
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(xAnimation).with(yAnimation);
        animatorSet.start();

        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (null != onDocked) {
                    onDocked.run();
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });
    }

    public void dockImmediately() {
        moveCenterTo(mDock.position());
    }

    public void moveCenterTo(@NonNull Point centerPosition) {
        Point cornerPosition = convertCenterToCorner(centerPosition);
        setX(cornerPosition.x);
        setY(cornerPosition.y);
        notifyListenersOfPositionChange(this);
    }

    private Point convertCenterToCorner(@NonNull Point centerPosition) {
        return new Point(
                centerPosition.x - (getTabSize() / 2),
                centerPosition.y - (getTabSize() / 2)
        );
    }

    private void notifyListenersOfDockChange() {
        for (OnPositionChangeListener listener : mOnPositionChangeListeners) {
            if (listener instanceof OnFloatingTabChangeListener) {
                ((OnFloatingTabChangeListener) listener).onDockChange(mDock);
            }
        }
    }

    // This method is declared in this class simply to make it clear that its part of our public
    // contract and not just an inherited method.
    public void setOnClickListener(@Nullable View.OnClickListener onClickListener) {
        super.setOnClickListener(onClickListener);
    }

    public interface OnFloatingTabChangeListener extends OnPositionChangeListener {
        void onDockChange(@NonNull Dock dock);
    }
}
