/**************SDK 层**************/

//--注册InputEventReceiver
ViewRootImpl.setView{
    inputChannel = new InputChannel();
    if (inputChannel != null) {
        if (mInputQueueCallback != null) {
            mInputQueue = new InputQueue();
            mInputQueueCallback.onInputQueueCreated(mInputQueue);
        }
        mInputEventReceiver = new WindowInputEventReceiver(inputChannel,
                Looper.myLooper());
    }
    mSyntheticInputStage = new SyntheticInputStage();
    InputStage viewPostImeStage = new ViewPostImeInputStage(mSyntheticInputStage); //最终派发事件的类
    InputStage nativePostImeStage = new NativePostImeInputStage(viewPostImeStage,
            "aq:native-post-ime:" + counterSuffix);
    InputStage earlyPostImeStage = new EarlyPostImeInputStage(nativePostImeStage);
    InputStage imeStage = new ImeInputStage(earlyPostImeStage,
            "aq:ime:" + counterSuffix);
    InputStage viewPreImeStage = new ViewPreImeInputStage(imeStage);
    InputStage nativePreImeStage = new NativePreImeInputStage(viewPreImeStage,
            "aq:native-pre-ime:" + counterSuffix);

    mFirstInputStage = nativePreImeStage;
    mFirstPostImeInputStage = earlyPostImeStage; //touch
    mPendingInputEventQueueLengthCounterName = "aq:pending:" + counterSuffix;
}
//接收native回调
WindowInputEventReceiver{
    onInputEvent(InputEvent){
        //从mQueuedInputEventPool中获取一个缓存的QueuedInputEvent复对象，最大缓存数10个
        //将获取的QueuedInputEvent插入到mPendingInputEventTail的尾部，便于doProcessInputEvents去处理
        enqueueInputEvent(event, this, 0, true){
            -->obtainQueuedInputEvent
            -->doProcessInputEvents
            -->deliverInputEvent-->mFirstPostImeInputStage.deliver{ 
                //责任链
                EarlyPostImeInputStage-->NativePostImeInputStage-->ViewPostImeInputStage.onProcess(QueuedInputEvent){
                    processPointerEvent-->mView.dispatchPointerEvent(MotionEvent){
                            if (event.isTouchEvent()) {
                                return dispatchTouchEvent(event);//传递到DecorView.dispatchTouchEvent
                            } else {
                                return dispatchGenericMotionEvent(event);
                            }
                        }
                    }
                }
            }
        }
    }
}

DecorView.dispatchTouchEvent{
    final Window.Callback cb = mWindow.getCallback(); //Activity
    return cb != null && !mWindow.isDestroyed() && mFeatureId < 0
            ? cb.dispatchTouchEvent(ev) : super.dispatchTouchEvent(ev);
}

Activity.dispatchTouchEvent{
    if (ev.getAction() == MotionEvent.ACTION_DOWN) {
        onUserInteraction();
    }
    //再次从Activity 传递到phonewindow, 再到DecorView.superDispatchTouchEvent-->super(View).dispatchTouchEvent
    if (getWindow().superDispatchTouchEvent(ev)) { 
        return true;
    }
    return onTouchEvent(ev);
}

ViewGroup.dispatchTouchEvent{
    //1.如果是ACTION_DOWN或设置了mFirstTouchTarget，调onInterceptTouchEvent检查,否则不调onInterceptTouchEvent，直接父类处理
    if (actionMasked == MotionEvent.ACTION_DOWN || mFirstTouchTarget != null) {
        //2. 如果viewGroup 调了requestDisallowInterceptTouchEvent， 则一定不拦截
        final boolean disallowIntercept = (mGroupFlags & FLAG_DISALLOW_INTERCEPT) != 0;
        if (!disallowIntercept) {
            intercepted = onInterceptTouchEvent(ev);
            ev.setAction(action); // restore action in case it was changed
        } else {
            intercepted = false;
        }
    } else {
        // 不是ACTION_DOWN，且没有设置mFirstTouchTarget，一定父view 拦截
        intercepted = true;
    }

    //3.不是取消，且父不拦截，且如果是ACTION_DOWN，遍历child 找到合适的 TouchTarget
    //  TouchTarget 
    if (!canceled && !intercepted) {
        if (actionMasked == MotionEvent.ACTION_DOWN...){
            if (newTouchTarget == null && childrenCount != 0){
                //3.1 Scan children from front to back. 注意这里会根据绘制顺序获取真正的child
                for (int i = childrenCount - 1; i >= 0; i--){
                    //3.2 检查点击坐标是否在view 区域内, 不在则continue
                    if (!child.canReceivePointerEvents()|| !isTransformedTouchPointInView(x, y, child, null)) {
                                ev.setTargetAccessibilityFocus(false);
                                continue;
                     }

                     //3.3 先检查已经在TouchTarget队列中， TouchTarget是个最大MAX_RECYCLED=32可以回收的stack，新加的TouchTarget在头部，最先被遍历
                    newTouchTarget = getTouchTarget(child);
                    if (newTouchTarget != null) {
                        // Child is already receiving touch within its bounds.
                        // Give it the new pointer in addition to the ones it is handling.
                        newTouchTarget.pointerIdBits |= idBitsToAssign;
                        break;
                    }
                    //3.4 如果当前点击的view 不在TouchTarget队列里 , 表示是第一次点击，先派发事件给当前view,
                    if (dispatchTransformedTouchEvent(ev, false, child, idBitsToAssign)) {
                        //3.5 事件被child处理后 添加一个TouchTarget 到队列头部
                        newTouchTarget = addTouchTarget(child, idBitsToAssign);
                        alreadyDispatchedToNewTouchTarget = true;
                        break;
                    }

                }
            }
        }

    }
    //4.如果没有找到TouchTarget，viewgroup自己处理,否则交给child
    if (mFirstTouchTarget == null) {
        // No touch targets so treat this as an ordinary view.
        handled = dispatchTransformedTouchEvent(ev, canceled, null,
                TouchTarget.ALL_POINTER_IDS);
    } else {
        //
        // Dispatch to touch targets, excluding the new touch target if we already
        // dispatched to it.  Cancel touch targets if necessary.
        if (alreadyDispatchedToNewTouchTarget && target == newTouchTarget) {
            handled = true;
        } else {
            if (dispatchTransformedTouchEvent(ev, cancelChild, target.child, target.pointerIdBits)) {
                    handled = true;
            }
        }
    }
    //5. 返回是否被消耗了
    return handled;

}


//Transforms a motion event into the coordinate space of a particular child view, 
//filters out irrelevant pointer ids, and overrides its action if necessary.
// If child is null, assumes the MotionEvent will be sent to this ViewGroup instead.
//如果参数child 为空，则直接调dispatchTouchEvent， 如果参数child 不为空，则对坐标进行转换，然后给child去处理事件，
ViewGroup.dispatchTransformedTouchEvent{
    final int oldAction = event.getAction();
    //如果是取消事件，先处理
    if (cancel || oldAction == MotionEvent.ACTION_CANCEL) {
        event.setAction(MotionEvent.ACTION_CANCEL);
        if (child == null) {
            handled = super.dispatchTouchEvent(event);
        } else {
            handled = child.dispatchTouchEvent(event);
        }
        event.setAction(oldAction);
        return handled;
    }

    
    if (child == null) {
        handled = super.dispatchTouchEvent(transformedEvent);
    } else {
        //滚动距离减去child的let, top坐标,然后再交给child 处理
        final float offsetX = mScrollX - child.mLeft;
        final float offsetY = mScrollY - child.mTop;
        transformedEvent.offsetLocation(offsetX, offsetY);
        if (! child.hasIdentityMatrix()) {
            transformedEvent.transform(child.getInverseMatrix());
        }

        handled = child.dispatchTouchEvent(transformedEvent);
    }
}

//如果设置了mOnTouchListener 则先调mOnTouchListener.onTouch，但是调后如果返回false 再调 onTouchEvent
View.dispatchTouchEvent{
    ListenerInfo li = mListenerInfo;
     if (li != null && li.mOnTouchListener != null && (mViewFlags & ENABLED_MASK) == ENABLED
            && li.mOnTouchListener.onTouch(this, event)) {
        result = true;
    }

    if (!result && onTouchEvent(event)) {
        result = true;
    }
}

View.onTouchEvent{
    final boolean clickable = ((viewFlags & CLICKABLE) == CLICKABLE || (viewFlags & LONG_CLICKABLE) == LONG_CLICKABLE)|| (viewFlags & CONTEXT_CLICKABLE) == CONTEXT_CLICKABLE;
    //1.先检查是否设置了mTouchDelegate, 设置了先调mTouchDelegate.onTouchEvent(event)，返回false 再继续处理，
    if (mTouchDelegate != null) {
        if (mTouchDelegate.onTouchEvent(event)) {
            return true;
        }
    }

    if (clickable || (viewFlags & TOOLTIP) == TOOLTIP) {
        switch (action) {
            //2. 检查是否pressed而且focused 然后post runner去处理Click
            case MotionEvent.ACTION_UP:
                boolean prepressed = (mPrivateFlags & PFLAG_PREPRESSED) != 0;
                if ((mPrivateFlags & PFLAG_PRESSED) != 0 || prepressed) {
                    // take focus if we don't have it already and we should in
                    // touch mode.
                    boolean focusTaken = false;
                    //2.1这里isFocused要是true，后面才处理点击
                    if (isFocusable() && isFocusableInTouchMode() && !isFocused()) { 
                        focusTaken = requestFocus();
                    }

                    // 2.2 Only perform take click actions if we were in the pressed state
                    if (!focusTaken) {
                        // Use a Runnable and post this rather than calling
                        // performClick directly. This lets other visual state
                        // of the view update before click actions start.
                        if (mPerformClick == null) {
                            mPerformClick = new PerformClick(){
                                final ListenerInfo li = mListenerInfo;
                                if (li != null && li.mOnClickListener != null) {
                                    playSoundEffect(SoundEffectConstants.CLICK);
                                    li.mOnClickListener.onClick(this);
                                } else {
                                }
                            }
                        }
                        post(mPerformClick))
                    }
            case MotionEvent.ACTION_DOWN:
                //设置pressed状态
                setPressed(true, x, y);
                //如果可以接收长按事件，postdelay 默认400ms去执行performLongClick
                checkForLongClick(ViewConfiguration.getLongPressTimeout(),x,y){
                    if ((mViewFlags & LONG_CLICKABLE) == LONG_CLICKABLE || (mViewFlags & TOOLTIP) == TOOLTIP) {
                        postDelayed(new CheckForLongPress(){
                            final ListenerInfo li = mListenerInfo;
                            if (li != null && li.mOnLongClickListener != null) {
                                handled = li.mOnLongClickListener.onLongClick(View.this);
                            }
                        }, 400)
                    }

                }
            case MotionEvent.ACTION_MOVE:
                //如果点击区域超出了view, 取消tab，长按事件，pressed状态
                if (!pointInView(x, y, touchSlop)) {
                    // Outside button
                    // Remove any future long press/tap checks
                    removeTapCallback();
                    removeLongPressCallback();
                    if ((mPrivateFlags & PFLAG_PRESSED) != 0) {
                        setPressed(false);
                    }
                }
                //如果是使劲按，立即抛出长按事件
                final boolean deepPress =motionClassification == MotionEvent.CLASSIFICATION_DEEP_PRESS;
                if (deepPress && hasPendingLongPressCallback()) {
                    // process the long click action immediately
                    removeLongPressCallback();
                    checkForLongClick(...)
                }
             
}

