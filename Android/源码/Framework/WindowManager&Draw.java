/****这里主要是讲java层****/
// 入口 WindowManagerImpl.addView =>mGlobal.addView=> ViewRootImpl().setView-> requestLayout->scheduleTraversals  => Choreographer.postCallback(Choreographer.CALLBACK_TRAVERSAL, TraversalRunnable)
//      =>Choreographer.scheduleFrameLocked=>Choreographer.scheduleVsyncLocked=>DisplayEventReceive.onVsync=>Choreographer.doFrame,doCallbacks(CALLBACK_TRAVERSAL)=>TraversalRunnable.run
//      =>ViewRootImpl.performTraversals

WindowManagerImpl{
    public final Surface mSurface = new Surface();
    private final SurfaceControl mSurfaceControl = new SurfaceControl();
    private final SurfaceSession mSurfaceSession = new SurfaceSession();

    private final WindowManagerGlobal mGlobal = WindowManagerGlobal.getInstance();


    getWindowSession{
        if (sWindowSession == null) {
            IWindowManager windowManager = getWindowManagerService();
        
            sWindowSession = windowManager.openSession( //see WindowManagerService.openSesstion
                            new IWindowSessionCallback.Stub() {
                                @Override
                                public void onAnimatorScaleChanged(float scale) {
                                    ValueAnimator.setDurationScale(scale);
                                }
                            });
        return sWindowSession;

    }

    addView(@NonNull View view, @NonNull ViewGroup.LayoutParams params) {
        applyTokens(params);
        mGlobal.addView(view, params, mContext.getDisplayNoVerify(), mParentWindow,mContext.getUserId()){
            // If there's no parent, then hardware acceleration for this view is
            // set from the application's hardware acceleration setting.
            //如果App 配置 开启了硬件加速，则window，view 绘制就由硬件加速绘制
            final Context context = view.getContext();
            if (context != null&& (context.getApplicationInfo().flags & ApplicationInfo.FLAG_HARDWARE_ACCELERATED) != 0) {
                wparams.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
            }
            //找父view 如果存在的话
             // If this is a panel window, then find the window it is being
            // attached to for future reference.
            if (wparams.type >= WindowManager.LayoutParams.FIRST_SUB_WINDOW &&
                    wparams.type <= WindowManager.LayoutParams.LAST_SUB_WINDOW) {
                final int count = mViews.size();
                for (int i = 0; i < count; i++) {
                    if (mRoots.get(i).mWindow.asBinder() == wparams.token) {
                        panelParentView = mViews.get(i);
                    }
                }
            }
            //创建viewRootimp
            root = new ViewRootImpl(view.getContext(), display,WindowManagerGlobal.getWindowSession(),false);
            //DecorView
            view.setLayoutParams(wparams);
            root.setView(view, wparams, panelParentView, userId); //see ViewRootImpl.setView
           
        }
    }
}

//ViewRootImpl 的输入事件相关的功能请查看事件分发文档。
public ViewRootImpl(@UiContext Context context, Display display, IWindowSession session , boolean useSfChoreographer){
    mThread = Thread.currentThread();

    public final Surface mSurface = new Surface();
    private final SurfaceControl mSurfaceControl = new SurfaceControl();

    private BLASTBufferQueue mBlastBufferQueue;
    private final SurfaceSession mSurfaceSession = new SurfaceSession();
    private final SurfaceSyncer mSurfaceSyncer = new SurfaceSyncer();

    mWindowSession = session
    //创建AttachInfo
    mAttachInfo = new View.AttachInfo(mWindowSession, mWindow, display, this, mHandler, this, context);
    //useSfChoreographer 默认是false
    mChoreographer = useSfChoreographer ? Choreographer.getSfInstance() : Choreographer.getInstance();
    mDisplayManager = (DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);

    private final DisplayListener mDisplayListener = new DisplayListener() {
        @Override
        public void onDisplayChanged(int displayId) {
        }
    }

    setView(View view, WindowManager.LayoutParams attrs, View panelParentView, int userId) {
        //DecorView
        mView = view;
        if (view instanceof RootViewSurfaceTaker) {
            //mSurfaceHolderCallback 为空，这里只有NativeActivity或者手动调了Window.takeSurface 才不为null.
            mSurfaceHolderCallback =((RootViewSurfaceTaker)view).willYouTakeTheSurface();
            if (mSurfaceHolderCallback != null) { 
                mSurfaceHolder = new TakenSurfaceHolder();
                mSurfaceHolder.setFormat(PixelFormat.UNKNOWN)
                mSurfaceHolder.addCallback(mSurfaceHolderCallback);
            }
        }

        if (mSurfaceHolder == null) {
            // While this is supposed to enable only, it can effectively disable
            // the acceleration too.
            //开启硬件加速
            enableHardwareAcceleration(attrs){
                //see WindowManagerGlobal.addView的时候默认根据App manifest配置
                final boolean hardwareAccelerated =(attrs.flags & WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED) != 0;
                if (hardwareAccelerated) {
                    //创建ThreadedRenderer
                    mAttachInfo.mThreadedRenderer = ThreadedRenderer.create(mContext, translucent,attrs.getTitle().toString());
                    if (mAttachInfo.mThreadedRenderer != null) {
                        if (mHardwareRendererObserver != null) {
                            mAttachInfo.mThreadedRenderer.addObserver(mHardwareRendererObserver);
                        }
                    }
                }
            }
            final boolean useMTRenderer = MT_RENDERER_AVAILABLE&& mAttachInfo.mThreadedRenderer != null;
        }
        mAdded = true;
        requestLayout(){
            if (!mHandlingLayoutInLayoutRequest) {
                checkThread(){
                    if (mThread != Thread.currentThread()) {
                        throw new CalledFromWrongThreadException
                    }
                }
                mLayoutRequested = true;
                scheduleTraversals(){
                    if (!mTraversalScheduled) {
                        mTraversalScheduled = true;
                        mTraversalBarrier = mHandler.getLooper().getQueue().postSyncBarrier();
                        //看下文
                        mChoreographer.postCallback(Choreographer.CALLBACK_TRAVERSAL, mTraversalRunnable, null);
                    }
                }
            }
        }
        //mWindowSession= WindowManagerGlobal.getWindowSession()== new Session(WindowManagerService, callback);
        res = mWindowSession.addToDisplayAsUser -> WindowManagerService.addWindow  

        registerListeners(){
            mDisplayManager.registerDisplayListener(mDisplayListener, mHandler);
        }
        if ((res & WindowManagerGlobal.ADD_FLAG_USE_BLAST) != 0) {
            mUseBLASTAdapter = true;
        }
        view.assignParent(this);
    }

    final class TraversalRunnable implements Runnable {
        @Override
        public void run() {
            doTraversal(){
                if (mTraversalScheduled) {
                    mTraversalScheduled = false;
                    mHandler.getLooper().getQueue().removeSyncBarrier(mTraversalBarrier);
                    //这里主要讨论绘制过程，其他measure, layout 在其他章节讲。
                    performTraversals(){
                        if(mFirst){
                            //onViewAttachedToWindow
                            host.dispatchAttachedToWindow(mAttachInfo, 0);
                            mAttachInfo.mTreeObserver.dispatchOnWindowAttachedChange(true);
                            dispatchApplyInsets(host);
                        }
                        boolean hadSurface = mSurface.isValid();//刚开始Surface 不valid
                        relayoutResult = relayoutWindow(params, viewVisibility, insetsPending){
                            //1.创建WindowSurfaceControl,并把它的SurfaceControl copy 给mSurfaceControl
                            relayoutResult = mWindowSession.relayout(mSurfaceControl)->mService@WindowManagerService.relayoutWindow(){
                                 //1.1.先找到windowstate,这个windowstate就是setView->mWindowSession.addToDisplayAsUser-> WindowManagerService.addWindow调用后创建保存的
                                final WindowState win = windowForClientLocked(session, client, false);
                                //1.2. 创建 WindowSurfaceController  ,并copy 给ViewRootImp中的outSurfaceControl
                                result = createSurfaceControl(outSurfaceControl, result, win, winAnimator){
                                    surfaceController = winAnimator.createSurfaceLocked(){
                                        mSurfaceController = new WindowSurfaceController(attrs.getTitle().toString(), format,flags, this, attrs.type);
                                    }
                                    
                                    if (surfaceController != null) {
                                        //Native COPY
                                        surfaceController.getSurfaceControl(outSurfaceControl);
                                    }
                                }
                            }
                            //2. 创建 surfcae并 copy 给ViewRootImpl mSurface
                            if (mSurfaceControl.isValid()) {
                                 //useBLAST() == true, 由WMS在addWindow的时候设置的
                                updateBlastSurfaceIfNeeded(){
                                    //1.创建BLASTBufferQueue 2.createSurface。3 transferFrom to ViewRootImpl mSurface
                                    mBlastBufferQueue = new BLASTBufferQueue(mTag, mSurfaceControl,mSurfaceSize.x, mSurfaceSize.y, mWindowAttributes.format);
                                    Surface blastSurface = mBlastBufferQueue.createSurface();
                                    // Only call transferFrom if the surface has changed to prevent inc the generation ID and
                                    // causing EGL resources to be recreated.
                                    mSurface.transferFrom(blastSurface);
                                }                            
                                
                                if (mAttachInfo.mThreadedRenderer != null) {
                                    mAttachInfo.mThreadedRenderer.setSurfaceControl(mSurfaceControl);
                                    mAttachInfo.mThreadedRenderer.setBlastBufferQueue(mBlastBufferQueue);
                                }
                            }
                        }
                        if (surfaceCreated) {
                            if (mAttachInfo.mThreadedRenderer != null) { //硬件加速
                                hwInitialized = mAttachInfo.mThreadedRenderer.initialize(mSurface);
                                if (hwInitialized && (host.mPrivateFlags & View.PFLAG_REQUEST_TRANSPARENT_REGIONS) == 0) {
                                    mAttachInfo.mThreadedRenderer.allocateBuffers();
                                }
                            }
                        }
                        
                        boolean cancelAndRedraw = mAttachInfo.mTreeObserver.dispatchOnPreDraw();
                        if (!cancelAndRedraw) { //当前的draw不cancle
                            createSyncIfNeeded(){
                                mSyncId = mSurfaceSyncer.setupSync(transaction -> {
                                    // Callback will be invoked on executor thread so post to main thread.
                                    mHandler.postAtFrontOfQueue(() -> {
                                        mSurfaceChangedTransaction.merge(transaction);
                                        reportDrawFinished(seqId);
                                    });
                                });
                                mSurfaceSyncer.addToSync(mSyncId, mSyncTarget);
                            }
                        }

                        performDraw()->draw(fullRedrawNeeded, usingAsyncReport && mSyncBuffer){
                            if (DEBUG_FPS) {
                                //这个会统计每1秒的FPS,即每秒绘制多少次。
                                trackFPS();
                            }
                            //这里会执行 public static void addFirstDrawHandler(Runnable callback)添加的irstDrawHandler
                            if (!sFirstDrawComplete) {
                                synchronized (sFirstDrawHandlers) {
                                    sFirstDrawComplete = true;
                                    final int count = sFirstDrawHandlers.size();
                                    for (int i = 0; i< count; i++) {
                                        mHandler.post(sFirstDrawHandlers.get(i));
                                    }
                                }
                            }
                            mAttachInfo.mTreeObserver.dispatchOnDraw();
                            if (!dirty.isEmpty() || mIsAnimating || accessibilityFocusDirty) {
                                if (isHardwareEnabled()) {
                                    mAttachInfo.mThreadedRenderer.draw(mView, mAttachInfo, this@ViewRootImpl>DrawCallbacks){
                                        updateRootDisplayList(view, callbacks@ViewRootImpl){
                                            RecordingCanvas canvas = mRootNode.beginRecording(mSurfaceWidth, mSurfaceHeight);
                                            final int saveCount = canvas.save();
                                            canvas.translate(mInsetLeft, mInsetTop);
                                            callbacks.onPreDraw(canvas);
                                            canvas.enableZ();
                                            //这里是关键
                                            canvas.drawRenderNode(
                                                view.updateDisplayListIfDirty(){
                                                    //renderNode = View.mRenderNode 在构造函数中初始化，mRenderNode = RenderNode.create(getClass().getName(), new ViewAnimationHostBridge(this))
                                                    final RecordingCanvas canvas = renderNode.beginRecording(width, height){
                                                        return RecordingCanvas.obtain(this, width, height){
                                                            //复用或者new 一个RecordingCanvas， 复用池最大POOL_LIMIT = 25
                                                        }
                                                    }

                                                    //layerType is set by   View.setLayerType
                                                    if (layerType == LAYER_TYPE_SOFTWARE) {
                                                        buildDrawingCache(true);{
                                                            //1.复用或者创建一个bitmap, 2.根据创建canvas。3 dispatchDraw or draw
                                                        }
                                                        Bitmap cache = getDrawingCache(true);
                                                        if (cache != null) {
                                                            //当做 bitmap 去绘制
                                                            canvas.drawBitmap(cache, 0, 0, mLayerPaint);
                                                        }
                                                    } else {
                                                        computeScroll();
                                                        canvas.translate(-mScrollX, -mScrollY);

                                                        // Fast path for layouts with no backgrounds
                                                        if ((mPrivateFlags & PFLAG_SKIP_DRAW) == PFLAG_SKIP_DRAW) {
                                                            dispatchDraw(canvas);
                                                            drawAutofilledHighlight(canvas);
                                                            if (mOverlay != null && !mOverlay.isEmpty()) {
                                                                mOverlay.getOverlayView().draw(canvas);
                                                            }
                                                        } else {
                                                            draw(canvas);
                                                        }
                                                    }
                                            )
                                            canvas.disableZ();
                                            callbacks.onPostDraw(canvas);
                                        }
                                        final FrameInfo frameInfo = attachInfo.mViewRootImpl.getUpdatedFrameInfo();
                                        int syncResult = syncAndDrawFrame(frameInfo);
                                    }
                                }else{
                                    drawSoftware(surface, mAttachInfo, xOffset, yOffset,scalingRequired, dirty, surfaceInsets){
                                        //draw3步，
                                        canvas = mSurface.lockCanvas(dirty);
                                        mView.draw(canvas);
                                        surface.unlockCanvasAndPost(canvas);
                                    }
                                }
                            }
                        }

                        if (!performDraw() && mSyncBufferCallback != null) {
                            mSyncBufferCallback.onBufferReady(null);
                        }
                    }
                }
            }
        }
    }
}

Choreographer{
    //默认true
    private static final boolean USE_VSYNC = SystemProperties.getBoolean("debug.choreographer.vsync", true);
    //默认FrameDisplayEventReceiver
    mDisplayEventReceiver = USE_VSYNC ? new FrameDisplayEventReceiver(looper, vsyncSource)

    private static final ThreadLocal<Choreographer> sThreadInstance = new ThreadLocal<Choreographer>() {
        @Override
        protected Choreographer initialValue() {
            Looper looper = Looper.myLooper();
            Choreographer choreographer = new Choreographer(looper, VSYNC_SOURCE_APP);
            if (looper == Looper.getMainLooper()) {
                mMainInstance = choreographer;
            }
            return choreographer;
        }
    };


    public static Choreographer getInstance() {
        return sThreadInstance.get();
    }

    mHandler = new FrameHandler(looper){
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_DO_FRAME:
                    doFrame(System.nanoTime(), 0, new DisplayEventReceiver.VsyncEventData());
                    break;
                case MSG_DO_SCHEDULE_VSYNC:
                    doScheduleVsync();
                    break;
                case MSG_DO_SCHEDULE_CALLBACK:
                    doScheduleCallback(msg.arg1);
                    break;
            }
        }
    }

    public void postCallback(int callbackType, Runnable action, Object token) {
        synchronized (mLock) {
            final long now = SystemClock.uptimeMillis();
            final long dueTime = now + delayMillis;
            mCallbackQueues[callbackType].addCallbackLocked(dueTime, action, token){
                //创建或者复用一个CallbackRecord， 根据dueTime时间顺序插入到合适的位置
                CallbackRecord callback = obtainCallbackLocked(dueTime, action, token);
            }

            if (dueTime <= now) {
                //第一次立即
                scheduleFrameLocked(now){
                    if (!mFrameScheduled) {
                        mFrameScheduled = true;
                        if (USE_VSYNC) {
                            if (isRunningOnLooperThreadLocked()){
                                //发起一次Vsync信号监听
                                scheduleVsyncLocked(){
                                    mDisplayEventReceiver@FrameDisplayEventReceiver.scheduleVsync()->.nativeScheduleVsync =>
                                        //timestampNanos : The timestamp of the pulse 这里是垂直脉冲时间
                                        .onVsync(long timestampNanos, long physicalDisplayId, int frame,vsyncEventData){
                                            // 防止传入的vsync事件完全饿死
                                            if (timestampNanos > now) {
                                                //Frame time is in the future
                                                timestampNanos = now;
                                            }
                                            mHavePendingVsync = true;
                                            mTimestampNanos = timestampNanos;
                                            mFrame = frame;
                                            mLastVsyncEventData = vsyncEventData;
                                            Message msg = Message.obtain(mHandler, this@FrameDisplayEventReceiver) =>[
                                                @Override
                                                public void run() {
                                                    mHavePendingVsync = false;
                                                    .Choreographer.doFrame(mTimestampNanos, mFrame, mLastVsyncEventData);
                                                }
                                            ]
                                            msg.setAsynchronous(true);
                                            //立即发送消息
                                            mHandler.sendMessageAtTime(msg, timestampNanos / TimeUtils.NANOS_PER_MS);
                                        }
                                    }
                                }
                            } else {
                                Message msg = mHandler.obtainMessage(MSG_DO_SCHEDULE_VSYNC);
                                msg.setAsynchronous(true);
                                mHandler.sendMessageAtFrontOfQueue(msg);
                            }
                        } 
                    }
                }
            } else {
                Message msg = mHandler.obtainMessage(MSG_DO_SCHEDULE_CALLBACK, action);-->doScheduleCallback-->scheduleFrameLocked
                msg.arg1 = callbackType;
                msg.setAsynchronous(true);
                mHandler.sendMessageAtTime(msg, dueTime);
            }
        }
    }

    //frameTimeNanos : The timestamp of the pulse 这里是垂直脉冲时间
    void doFrame(long frameTimeNanos, int frame,DisplayEventReceiver.VsyncEventData vsyncEventData){
        //The current interval between frames in ns.
        final long frameIntervalNanos = vsyncEventData.frameInterval;
        startNanos = System.nanoTime();
        final long jitterNanos = startNanos - frameTimeNanos;
        //现在时间-脉冲信号开始的时间 > 2帧之间的间隔， 说明丢帧了
        if (jitterNanos >= frameIntervalNanos) { 
            lastFrameOffset = jitterNanos % frameIntervalNanos;
            //更新脉冲开始时间
            frameTimeNanos = startNanos - lastFrameOffset;
            frameData.updateFrameData(frameTimeNanos);
        }

        //脉冲开始时间比上一帧脉冲开始时间晚，跳过，重新发起vsync
        if (frameTimeNanos < mLastFrameTimeNanos) {
            Log.d(TAG, "Frame time appears to be going backwards.  May be due to a "previously skipped frame.  Waiting for next vsync.");
            scheduleVsyncLocked();
            return
        }
        mFrameScheduled = false;
        mLastFrameTimeNanos = frameTimeNanos;
        mLastFrameIntervalNanos = frameIntervalNanos;
        mLastVsyncEventData = vsyncEventData;

        mFrameInfo.markInputHandlingStart();
        //1.先处理输入事件
        doCallbacks(Choreographer.CALLBACK_INPUT, frameData, frameIntervalNanos);
        mFrameInfo.markAnimationsStart();
        //2. 处理动画
        doCallbacks(Choreographer.CALLBACK_ANIMATION, frameData, frameIntervalNanos);

        doCallbacks(Choreographer.CALLBACK_INSETS_ANIMATION, frameData,frameIntervalNanos);

        //3.处理layout和draw
        mFrameInfo.markPerformTraversalsStart();
        doCallbacks(Choreographer.CALLBACK_TRAVERSAL, frameData, frameIntervalNanos);
        //处理post-draw operations
        doCallbacks(Choreographer.CALLBACK_COMMIT, frameData, frameIntervalNanos);
    }

    void doCallbacks(int callbackType, FrameData frameData, long frameIntervalNanos) {
        final long now = System.nanoTime();
        //找出时间小于now的所有callbacks
        callbacks = mCallbackQueues[callbackType].extractDueCallbacksLocked(now / TimeUtils.NANOS_PER_MS);
        for (CallbackRecord c = callbacks; c != null; c = c.next) {
            c.run(frameData){
                if (token == VSYNC_CALLBACK_TOKEN) {
                    //执行postVsyncCallback调用的VsyncCallback的回调
                    ((VsyncCallback) action).onVsync(frameData);
                } else {
                    run(frameData.getFrameTimeNanos()){
                        //执行postFrameCallback调用的FrameCallback回调
                        if (token == FRAME_CALLBACK_TOKEN) {
                            ((FrameCallback)action).doFrame(frameTimeNanos);
                        } else {
                            //执行Runnable 这个是ViewRootImp 中的TraversalRunnable
                            ((Runnable)action).run(); //see ViewRootImp.TraversalRunnable
                        }
                    }
                }
            }
        }
    }
}


WindowManagerService{
    mRoot = new RootWindowContainer(this);
    mUseBLAST = Settings.Global.getInt(resolver,Settings.Global.DEVELOPMENT_USE_BLAST_ADAPTER_VR, 1) == 1;

    openSession(IWindowSessionCallback callback) {
        return new Session(this, callback);{
            addToDisplayAsUser{
                WindowManagerService.addWindow 
            }
        }
    }

    addWindow(Session session, IWindow client, LayoutParams attrs{
        //根据窗口类型，检查窗口
        //应用程序窗口：type值范围是1~99，Activity就是一个典型的应用程序窗口，type值是TYPE_BASE_APPLICATION，WindowManager的LayoutParams默认type值是TYPE_APPLICATION。
        //子窗口：type值范围是1000~1999，PupupWindow就是一个典型的子窗口，type值是TYPE_APPLICATION_PANEL，子窗口不能独立存在，必须依附于父窗口。
        //系统窗口：type值范围是2000~2999     
        int res = mPolicy@PhoneWindowManager.checkAddPermission(attrs.type)

        final DisplayContent displayContent = getDisplayContentOrCreate(displayId, attrs.token);

        final WindowState win = new WindowState
        if (type == TYPE_TOAST) {
            //win.mAttrs.hideTimeoutMilliseconds = (duration == Toast.LENGTH_LONG) ? LONG_DURATION_TIMEOUT(7000) : SHORT_DURATION_TIMEOUT(4000);
            mH.sendMessageDelayed(mH.obtainMessage(H.WINDOW_HIDE_TIMEOUT = 52, win,win.mAttrs.hideTimeoutMilliseconds)
        }

        if (mUseBLAST) { //默认是true
            res |= WindowManagerGlobal.ADD_FLAG_USE_BLAST;
        }

        win.attach(){
            mSession.windowAddedLocked(){
                mSurfaceSession = new SurfaceSession();
            }
        }
        mWindowMap.put(client.asBinder(), win);
        win.initAppOpsState();
        win.mToken.addWindow(win{
            if (mSurfaceControl == null) {
                //创建SurfaceControl
                createSurfaceControl(true /* force */);
    
                // Layers could have been assigned before the surface was created, update them again
                reassignLayer(getSyncTransaction());
            }
        }
        displayPolicy.addWindowLw(win, attrs);
        if (type == TYPE_APPLICATION_STARTING && activity != null) {
            activity.attachStartingWindow(win);

        } else if (type == TYPE_INPUT_METHOD
                // IME window is always touchable.
                // Ignore non-touchable windows e.g. Stylus InkWindow.java.
                && (win.getAttrs().flags & FLAG_NOT_TOUCHABLE) == 0) {
            displayContent.setInputMethodWindowLocked(win);
            }

    }
}
