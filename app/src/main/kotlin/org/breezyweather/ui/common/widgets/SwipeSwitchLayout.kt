/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 *
 * Breezy Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Breezy Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package org.breezyweather.ui.common.widgets

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.view.isNotEmpty
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Swipes between locations, like pages: the content follows the finger one to one, and switches
 * when dragged past [SWITCH_DISTANCE_RATIO] of the width or flicked that way. The current page then
 * slides off and the new location slides in from the other side; a drag that falls short settles
 * back, without bouncing.
 *
 * Horizontal scrolling inside the content (the hour strip...) never switches locations: those
 * areas claim their drags (see HorizontalRegionsComposeView), and nested scrolling is not used.
 */
class SwipeSwitchLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private var mTarget: View? = null
    private var mSettleAnimation: SpringAnimation? = null
    private var mPageAnimation: Animator? = null
    private var mSwitchListener: OnSwitchListener? = null
    private var mPageSwipeListener: OnPagerSwipeListener? = null
    var totalCount = 1
        private set
    private var mPosition = 0

    /** Drag distance, px: negative towards the next location (finger going left). */
    private var mSwipeDistance = 0f
    private var mLastX = 0f
    private var mLastY = 0f
    private var mDownX = 0f
    private var mDownY = 0f
    private val mTouchSlop: Int
    private val mMinFlingVelocity: Float
    private var mVelocityTracker: VelocityTracker? = null

    /** Speed at the last movement (px/s) and when it was, for a finger lifted right after moving. */
    private var mLastMoveVelocity = 0f
    private var mLastMoveTime = 0L
    private var mIsBeingDragged = false
    private var mIsHorizontalDragged = false
    private var mPassedTrigger = false

    /** Called whenever the content moves sideways (glass drawn over it follows). */
    var onContentMoved: (() -> Unit)? = null

    /** Direction of the switch whose new page is still to slide in, 0 for none. */
    private var mPendingEnterDirection = 0

    interface OnSwitchListener {
        fun onSwiped(swipeDirection: Int, progress: Float)
        fun onSwitched(swipeDirection: Int)
    }

    interface OnPagerSwipeListener {
        fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int)
        fun onPageSelected(position: Int)
    }

    init {
        val configuration = ViewConfiguration.get(getContext())
        mTouchSlop = configuration.scaledTouchSlop
        mMinFlingVelocity = FLING_VELOCITY_DP * resources.displayMetrics.density
    }

    private val switchDistance: Float
        get() = (width * SWITCH_DISTANCE_RATIO).coerceAtLeast(1f)

    private fun ensureTarget(): View? {
        if (mTarget == null && isNotEmpty()) mTarget = getChildAt(0)
        return mTarget
    }

    // touch.

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isEnabled || ensureTarget() == null) return false
        trackVelocity(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startTouch(ev)
            }
            MotionEvent.ACTION_MOVE -> if (!mIsBeingDragged) {
                val dx = ev.x - mDownX
                val dy = ev.y - mDownY
                if (abs(dx) > mTouchSlop || abs(dy) > mTouchSlop) {
                    mIsBeingDragged = true
                    // Clearly horizontal only: diagonal drags keep scrolling the page
                    mIsHorizontalDragged = abs(dx) > abs(dy) * 1.2f
                    if (mIsHorizontalDragged) {
                        // Count the whole movement since the finger came down: a quick flick may
                        // only bring a couple of touch events, and none of it should be lost
                        mSwipeDistance += dx
                        mLastX = ev.x
                        applyDistance()
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endTouch()
        }
        return mIsBeingDragged && mIsHorizontalDragged
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!isEnabled || ensureTarget() == null) return false
        trackVelocity(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> startTouch(ev)
            MotionEvent.ACTION_MOVE -> if (mIsBeingDragged && mIsHorizontalDragged) {
                mVelocityTracker?.let {
                    it.computeCurrentVelocity(1000)
                    mLastMoveVelocity = it.xVelocity
                    mLastMoveTime = ev.eventTime
                }
                mSwipeDistance += ev.x - mLastX
                mLastX = ev.x
                applyDistance()
                // A tick when the drag gets far enough to switch on release
                val passed = abs(mSwipeDistance) >= switchDistance
                if (passed != mPassedTrigger) {
                    mPassedTrigger = passed
                    if (passed) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
            // A cancel (something above took the gesture over) ends the swipe like a release, with
            // the speed measured so far, so a quick flick isn't lost
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val lifted = mVelocityTracker?.let {
                    it.computeCurrentVelocity(1000)
                    it.xVelocity
                } ?: 0f
                // The tracker can read a quick flick as "stopped" (a short pause before lifting, or too
                // few touch events): also take the speed at the last movement, if the finger lifted
                // right after it, and for a quick gesture its average speed since touching down
                val lastMove = if (ev.eventTime - mLastMoveTime <= LIFT_GRACE_MS) mLastMoveVelocity else 0f
                val duration = ev.eventTime - ev.downTime
                val average = if (duration in 1..QUICK_GESTURE_MS) (ev.x - mDownX) / duration * 1000f else 0f
                val velocity = listOf(lifted, lastMove, average).maxBy { abs(it) }
                release(velocity)
                endTouch()
            }
        }
        return true
    }

    private fun startTouch(ev: MotionEvent) {
        // Grabbing the page mid-animation takes it from where it is
        cancelSettle()
        mDownX = ev.x
        mDownY = ev.y
        mLastX = ev.x
        mLastY = ev.y
        mIsBeingDragged = false
        mIsHorizontalDragged = false
        mLastMoveVelocity = 0f
        mLastMoveTime = 0L
        mPassedTrigger = abs(mSwipeDistance) >= switchDistance
    }

    private fun endTouch() {
        mIsBeingDragged = false
        mIsHorizontalDragged = false
        mVelocityTracker?.recycle()
        mVelocityTracker = null
    }

    private fun trackVelocity(ev: MotionEvent) {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            mVelocityTracker?.clear()
        }
        (mVelocityTracker ?: VelocityTracker.obtain().also { mVelocityTracker = it }).addMovement(ev)
    }

    // control.

    /**
     * Called when the content was rebuilt (e.g. for the new location after a switch): puts the page
     * back in place, sliding the new one in if a switch just happened.
     */
    fun reset() {
        cancelSettle()
        mIsBeingDragged = false
        mIsHorizontalDragged = false
        val enterDirection = mPendingEnterDirection
        mPendingEnterDirection = 0
        if (enterDirection != 0 && mTarget != null && width > 0) {
            slideIn(enterDirection)
        } else {
            mPageAnimation?.cancel()
            mSwipeDistance = 0f
            applyDistance()
        }
    }

    private fun cancelSettle() {
        mSettleAnimation?.cancel()
        mSettleAnimation = null
    }

    /** Moves the content with the drag, and reports the progress. */
    private fun applyDistance() {
        val target = mTarget ?: return
        val progress = (abs(mSwipeDistance) / switchDistance).coerceIn(0f, 1f)
        target.translationX = mSwipeDistance
        onContentMoved?.invoke()
        // Fades a little, so the switch reads as leaving this place
        target.alpha = 1f - 0.35f * min(1f, abs(mSwipeDistance) / width.coerceAtLeast(1))
        val swipeDirection = if (mSwipeDistance < 0) SWIPE_DIRECTION_LEFT else SWIPE_DIRECTION_RIGHT
        mSwitchListener?.onSwiped(swipeDirection, progress)
        mPageSwipeListener?.let {
            val page = width.coerceAtLeast(1).toFloat()
            val offset = min(1f, abs(mSwipeDistance) / page)
            if (mSwipeDistance > 0) {
                it.onPageScrolled(mPosition - 1, 1 - offset, (page * (1 - offset)).roundToInt())
            } else {
                it.onPageScrolled(mPosition, offset, (page * offset).roundToInt())
            }
        }
    }

    /**
     * Switches when dragged far enough, or flicked in the direction of the drag; otherwise settles back.
     * @param velocity px/s, positive to the right
     */
    private fun release(velocity: Float) {
        if (mSwipeDistance == 0f) return
        val flicked = abs(velocity) >= mMinFlingVelocity &&
            sign(velocity) == sign(mSwipeDistance) &&
            abs(mSwipeDistance) > mTouchSlop
        val farEnough = abs(mSwipeDistance) >= switchDistance &&
            // A firm flick back towards the start cancels
            !(abs(velocity) >= mMinFlingVelocity && sign(velocity) != sign(mSwipeDistance))
        if (totalCount > 1 && (flicked || farEnough)) {
            slideOut(if (mSwipeDistance < 0) SWIPE_DIRECTION_LEFT else SWIPE_DIRECTION_RIGHT, velocity)
        } else {
            settleBack(velocity)
        }
    }

    private fun settleBack(velocity: Float) {
        cancelSettle()
        mSettleAnimation = SpringAnimation(FloatValueHolder(mSwipeDistance)).apply {
            spring = SpringForce(0f)
                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
                .setStiffness(SpringForce.STIFFNESS_MEDIUM)
            setStartVelocity(velocity)
            addUpdateListener { _: DynamicAnimation<*>?, value: Float, _: Float ->
                mSwipeDistance = value
                applyDistance()
            }
        }.also { it.start() }
    }

    /** The current page leaves to the side it was pushed, then the location switches. */
    private fun slideOut(swipeDirection: Int, velocity: Float) {
        cancelSettle()
        mPageAnimation?.cancel()
        val end = swipeDirection * width.toFloat()
        val remaining = abs(end - mSwipeDistance)
        // As fast as the finger was going, within limits
        val duration = if (abs(velocity) > 1f) {
            (remaining / abs(velocity) * 1000f).coerceIn(90f, 220f)
        } else {
            200f
        }.toLong()
        mPageAnimation = ValueAnimator.ofFloat(mSwipeDistance, end).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                mSwipeDistance = it.animatedValue as Float
                applyDistance()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    position = swipeDirection
                    mPendingEnterDirection = swipeDirection
                    mSwitchListener?.onSwitched(swipeDirection)
                    mPageSwipeListener?.onPageSelected(mPosition)
                    // In case the content isn't rebuilt (and doesn't call reset), still bring it in
                    postDelayed({ if (mPendingEnterDirection != 0) reset() }, 250)
                }
            })
        }.also { it.start() }
    }

    /** The new location's page comes in from the side opposite to where the old one left. */
    private fun slideIn(swipeDirection: Int) {
        mPageAnimation?.cancel()
        val start = -swipeDirection * width * ENTER_OFFSET_RATIO
        mPageAnimation = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                val f = it.animatedValue as Float
                mSwipeDistance = 0f
                mTarget?.translationX = start * (1f - f)
                mTarget?.alpha = 0.3f + 0.7f * f
                onContentMoved?.invoke()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    mTarget?.translationX = 0f
                    mTarget?.alpha = 1f
                    onContentMoved?.invoke()
                    mSwitchListener?.onSwiped(SWIPE_DIRECTION_LEFT, 0f)
                }
            })
        }.also { it.start() }
    }

    // interface.
    fun setData(currentIndex: Int, pageCount: Int) {
        if (currentIndex !in 0..<pageCount) {
            // Ignore, happens when location list is empty (initial install)
        } else {
            mPosition = currentIndex
            totalCount = pageCount
        }
    }

    var position: Int
        get() = mPosition
        private set(swipeDirection) {
            when (swipeDirection) {
                SWIPE_DIRECTION_LEFT -> mPosition++
                SWIPE_DIRECTION_RIGHT -> mPosition--
            }
            if (mPosition < 0) {
                mPosition = totalCount - 1
            } else if (mPosition > totalCount - 1) {
                mPosition = 0
            }
        }

    fun setOnSwitchListener(l: OnSwitchListener?) {
        mSwitchListener = l
    }

    fun setOnPageSwipeListener(l: OnPagerSwipeListener?) {
        mPageSwipeListener = l
    }

    companion object {
        /** How far to drag (of the width) to switch on release without a flick. */
        private const val SWITCH_DISTANCE_RATIO = 0.3f

        /** Flick speed that switches, in dp/s. */
        private const val FLING_VELOCITY_DP = 600f

        /** How long after the last movement a lifted finger still counts as flicking, in ms. */
        private const val LIFT_GRACE_MS = 80L

        /** Gestures up to this long (ms) also count their average speed as a flick speed. */
        private const val QUICK_GESTURE_MS = 250L

        /** Where the new page starts sliding in from, of the width. */
        private const val ENTER_OFFSET_RATIO = 0.3f

        const val SWIPE_DIRECTION_LEFT = -1
        const val SWIPE_DIRECTION_RIGHT = 1
    }
}
