/********这里主要是讲WindowManager&Draw.java中 的c++ 层********/

/**** 1. Surface 的创建 *****/
Surface.cpp{
    // mSurfaceTexture is the interface to the surface texture server. All
    // operations on the surface texture client ultimately translate into
    // interactions with the server using this interface.
    // TODO: rename to mBufferProducer
    sp<IGraphicBufferProducer> mGraphicBufferProducer;

    // mSlots stores the buffers that have been allocated for each buffer slot.
    // It is initialized to null pointers, and gets filled in with the result of
    // IGraphicBufferProducer::requestBuffer when the client dequeues a buffer from a
    // slot that has not yet been used. The buffer allocated to a slot will also
    // be replaced if the requested buffer usage or geometry differs from that
    // of the buffer allocated to a slot.
    BufferSlot mSlots[NUM_BUFFER_SLOTS];

    // must be used from the lock/unlock thread
    sp<GraphicBuffer>           mLockedBuffer;
    sp<GraphicBuffer>           mPostedBuffer;
}


ViewRootImp.performTraversals->updateBlastSurfaceIfNeeded-> new BLASTBufferQueue(){
    sp<BufferQueueCore> core(new BufferQueueCore());
    sp<IGraphicBufferProducer> producer(new BBQBufferQueueProducer(core));
    sp<BufferQueueConsumer> consumer(new BufferQueueConsumer(core));
    consumer->setAllowExtraAcquire(true);
    &mProducer = producer;
    &mConsumer = consumer;
}.createSurface->nativeGetSurface -> BLASTBufferQueue.cpp.getSurface{
    return new BBQSurface(mProducer, true, scHandle, this)
}


/****2. 软件绘制，
 * 接着 drawSoftware*****
    * drawSoftware(surface, mAttachInfo, xOffset, yOffset,scalingRequired, dirty, surfaceInsets){
    *   //draw3步，
    *   //Step 1.
    *   canvas = mSurface.lockCanvas(dirty);
    *   //Step 2.
    *   //这里是调一系列的canvas的draw 方法 如： drawRect, drawBitmap =>JNI nDrawRect=> android_graphics_Canvas.cpp 对应的drawRect 等方法=>SkiaCanvas.cpp的drawRect=>SkCanvas.cpp的onDrawRect
    *   mView.draw(canvas); 
    *  // Step 3.
    *   surface.unlockCanvasAndPost(canvas);
    *}
 *****/

//Surface.java => android_view_Surface.cpp => Surface.cpp
//Cavas.java => android_graphics_Canvas.cpp=>SkiaCanvas.cpp =>(内部封装了SkCanvas.cpp)
Surface{
    //java的Canvas 硬件加速没有开启的时候 本质上是SkiaCanvas
    mCanvas = new CompatibleCanvas()>new Canvas(){
        if (!isHardwareAccelerated()) {
            //没有bitmap 0 means no native bitmap
            mNativeCanvasWrapper = nInitRaster(0)=>android_graphics_Canvas.cpp.initRaster{
                Canvas::create_canvas=>SkiaCanvas.cpp{
                    Canvas* Canvas::create_canvas(const SkBitmap& bitmap) {
                        return new SkiaCanvas(bitmap);
                    }
                }
            }
            mFinalizer = NoImagePreloadHolder.sRegistry.registerNativeAllocation(this, mNativeCanvasWrapper);
        } 
    }

    lockCanvas(dirty){
        mLockedObject = nativeLockCanvas(mNativeObject, mCanvas, inOutDirty)=>android_view_Surface.cpp.nativeLockCanvas(JNIEnv* env, jclass clazz,jlong nativeObject, jobject canvasObj, jobject dirtyRectObj){
            ANativeWindow_Buffer buffer;
            //从mGraphicBufferProducer 申请一块buf 给 buffer，
            status_t err = surface->lock(&buffer, dirtyRectPtr){
                //
                if (!mConnectedToCpu) {
                    int err = Surface::connect(NATIVE_WINDOW_API_CPU){
                         IGraphicBufferProducer::QueueBufferOutput output;
                        int err = mGraphicBufferProducer->connect(listener = StubProducerListener(), api =NATIVE_WINDOW_API_CPU, mProducerControlledByApp = true, &output);
                    }
                }

                ANativeWindowBuffer* out;
                int fenceFd = -1;
                //从mGraphicBufferProducer 申请一块buf 给 out
                status_t err = dequeueBuffer(&out, &fenceFd){
                    //优先从mSlots中取一个。没有就创建一个新的
                    status_t result = mGraphicBufferProducer->dequeueBuffer(&buf, &fence, dqInput.width...);
                    sp<GraphicBuffer>& gbuf(mSlots[buf].buffer);
                    *out = gbuf.get();
                }
                //将out作为backBuffer 
                sp<GraphicBuffer> backBuffer(GraphicBuffer::getSelf(out));
                // 如果只有mPostedBuffer 则将其作为frontBuffer， 如果frontBuffer存在且宽高大小跟backBuffer一样，直接将frontBuffer的数据copy 给backBuffer
                const sp<GraphicBuffer>& frontBuffer(mPostedBuffer);
                const bool canCopyBack = (frontBuffer != nullptr &&
                        backBuffer->width  == frontBuffer->width &&
                        backBuffer->height == frontBuffer->height &&
                        backBuffer->format == frontBuffer->format);
                if (canCopyBack) {
                    // copy the area that is invalid and not repainted this round
                    const Region copyback(mDirtyRegion.subtract(newDirtyRegion));
                    if (!copyback.isEmpty()) {
                        copyBlt(backBuffer, frontBuffer, copyback, &fenceFd);
                    }
                }
                status_t res = backBuffer->lockAsync(GRALLOC_USAGE_SW_READ_OFTEN | GRALLOC_USAGE_SW_WRITE_OFTEN,newDirtyRegion.bounds(), &vaddr, fenceFd);
                //将backBuffer 赋值给 mLockedBuffer
                mLockedBuffer = backBuffer;
                outBuffer->width  = backBuffer->width;
                outBuffer->height = backBuffer->height;
                outBuffer->stride = backBuffer->stride;
                outBuffer->format = backBuffer->format;
                outBuffer->bits   = vaddr;

            }

            graphics::Canvas canvas(env, canvasObj);
            //Converts a buffer and dataspace into an SkBitmap, 
            //然后canvas.setBitmap(SkBitmap){ mCanvasOwned.reset(new SkCanvas(bitmap));mCanvas = mCanvasOwned.get();} 创建一个新的SkCanvas
            canvas.setBuffer(&buffer, static_cast<int32_t>(surface->getBuffersDataSpace()));

            if (dirtyRectPtr) {
                canvas.clipRect({dirtyRect.left, dirtyRect.top, dirtyRect.right, dirtyRect.bottom});
            }

            // Create another reference to the surface and return it.  This reference
            // should be passed to nativeUnlockCanvasAndPost in place of mNativeObject,
            // because the latter could be replaced while the surface is locked.
            sp<Surface> lockedSurface(surface);
            lockedSurface->incStrong(&sRefBaseOwner);
            return (jlong) lockedSurface.get();
        }
        return mCanvas;
    }

    unlockCanvasAndPost{
         synchronized (mLock) {
            checkNotReleasedLocked();

            if (mHwuiContext != null) { //set by lockHardwareCanvas
                mHwuiContext.unlockAndPost(canvas);
            } else {
                unlockSwCanvasAndPost(canvas){
                    try {
                        nativeUnlockCanvasAndPost(mLockedObject, canvas){
                            graphics::Canvas canvas(env, canvasObj);
                            canvas.setBuffer(nullptr, ADATASPACE_UNKNOWN);
                            // unlock surface
                            surface->unlockAndPost(){
                                int fd = -1;
                                status_t err = mLockedBuffer->unlockAsync(&fd);
                                err = queueBuffer(mLockedBuffer.get(), fd){
                                    //根据buffer的信息包括大小尺寸构造出QueueBufferInput input
                                    getQueueBufferInputLocked(buffer, fenceFd, mTimestamp, &input);
                                    sp<Fence> fence = input.fence;
                                    //这个方法很关键
                                    status_t err = mGraphicBufferProducer@BufferQueueProducer->queueBuffer(slot, input, &output){
                                        //从slots中获取mGraphicBuffer即上文lockCanvas创建的GraphicBuffer
                                        const sp<GraphicBuffer>& graphicBuffer(mSlots[slot].mGraphicBuffer);
                                        //根据graphicBuffer的信息构造一个BufferItem
                                        BufferItem item;
                                        item.mSlot = slot;
                                        item.mGraphicBuffer = mSlots[slot].mGraphicBuffer;
                                        //如果mCore@BufferQueueCore的mQueue为空，直接插入item
                                        if (mCore->mQueue.empty()) {
                                            // When the queue is empty, we can ignore mDequeueBufferCannotBlock
                                            // and simply queue this buffer
                                            mCore->mQueue.push_back(item);
                                            frameAvailableListener = mCore->mConsumerListener;
                                        else{
                                            // When the queue is not empty, we need to look at the last buffer
                                            // in the queue to see if we need to replace it
                                            const BufferItem& last = mCore->mQueue.itemAt(mCore->mQueue.size() - 1);
                                            if (last.mIsDroppable) {
                                                     // Overwrite the droppable buffer with the incoming one
                                                mCore->mQueue.editItemAt(mCore->mQueue.size() - 1) = item;
                                                frameReplacedListener = mCore->mConsumerListener;
                                            } else {
                                                mCore->mQueue.push_back(item);
                                                frameAvailableListener = mCore->mConsumerListener;
                                            }
                                        }
                                        mCore->mBufferHasBeenQueued = true;
                                        mCore->mDequeueCondition.notify_all();
                                        mCore->mLastQueuedSlot = slot;
                                        output->numPendingBuffers = static_cast<uint32_t>(mCore->mQueue.size());
                                        output->nextFrameNumber = mCore->mFrameCounter + 1;

                                        if (frameAvailableListener != nullptr) {
                                            frameAvailableListener->onFrameAvailable(item) ==>BLASTBufferQueue.onFrameAvailable{
                                                acquireNextBufferLocked(std::nullopt){
                                                    SurfaceComposerClient::Transaction localTransaction;
                                                    bool applyTransaction = true;
                                                    SurfaceComposerClient::Transaction* t = &localTransaction;
                                                    BufferItem bufferItem;
                                                    status_t status =mBufferItemConsumer->acquireBuffer(&bufferItem, 0 /* expectedPresent */, false);
                                                    auto buffer = bufferItem.mGraphicBuffer;
                                                    mNumFrameAvailable--;
                                                    t->setBuffer(mSurfaceControl, buffer, releaseCallbackId, releaseBufferCallback);
                                                    if (applyTransaction) {
                                                        //请看下文第四部分SurfaceFlinger , SurfaceComposerClient::Transaction.apply
                                                        t@SurfaceComposerClient::Transaction->setApplyToken(mApplyToken).apply(); =>SurfaceComposerClient
                                                    }
                                                }
                                            }
                                        } else if (frameReplacedListener != nullptr) {
                                            frameReplacedListener->onFrameReplaced(item) ==>BLASTBufferQueue.onFrameAvailable{
                                                const bool nextTransactionSet = mNextTransaction != nullptr;
                                            }
                                        }
                                    }
                                    onBufferQueuedLocked(i, fence, output);
                                }
                                mPostedBuffer = mLockedBuffer;
                                mLockedBuffer = nullptr;
                            }
                        }
                    } finally {
                        nativeRelease(mLockedObject);
                        mLockedObject = 0;
                    }
                }
            }
        }

    }
}

/****3. 硬件绘制，


/****4. SurfaceFlinger，
SurfaceFlinger

SurfaceComposerClient::Transaction{

    apply{

    }

}







