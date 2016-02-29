package jp.co.pixela.exoplayerwrapper;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Unity用サーフェステクスチャ
 * @note    TODO: Unity側でダミーの gameobject を上に配置しないとグラデーション表示になってしまう問題が残っている
 *              GL_BLEND あたりの設定・解除あたりがあやしい
 */
public class UnitySurfaceTexture implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "ExoPlayerWrapper";

    /** ロック */
    private final Object mLock = new Object();

    /** フレームバッファ */
    private int mFrameBuf = 0;
    /** サーフェス */
    private Surface mSurface = null;
    /** サーフェステクスチャ */
    private SurfaceTexture mSurTex = null;
    /** テクスチャ */
    private int mTex = 0;
    /** Unityテクスチャ */
    private int mUnityTex = 0;
    /** Unityテクスチャの幅 */
    private int mUnityTexWidth = 0;
    /** Unityテクスチャの幅高さ*/
    private int mUnityTexHeight = 0;
    /** フレーム更新されたかどうか */
    private boolean mIsUpdated = false;

    /**
     * コンストラクタ
     */
    public UnitySurfaceTexture() {
        //pxLog("UnitySurfaceTexture constructor");
    }

    /**
     * Unityテクスチャを設定する
     * 
     * @note ユニティスレッドから呼び出すこと
     * 
     * @param texId
     *            テクスチャID
     * @param texture_width
     *            テクスチャの幅
     * @param texture_height
     *            テクスチャの高さ
     */
    public void setUnityTexture(int texId, int texture_width, int texture_height) {
        pxLog("setUnityTexture: tex=" + texId + ", width=" + texture_width + ", height=" + texture_height);
        synchronized (mLock) {
            pxLog("setunity0");
            if (mUnityTex == texId && mUnityTexWidth == texture_width && mUnityTexHeight == texture_height) {
                System.out.println("Unity 0");
                System.out.println(String.valueOf(mUnityTex) + "," +
                                String.valueOf(mUnityTexWidth) + "," +
                                String.valueOf(mUnityTexHeight)
                );
                pxLog("setunity0.5 xxx");

                return;
            }

            pxLog("setunity1");
            mUnityTex = texId;
            mUnityTexWidth = texture_width;
            mUnityTexHeight = texture_height;

            createFBO();
            pxLog("setunity2");
        }
    }

    /**
     * サーフェス取得
     * 
     * @note ユニティスレッドから呼び出すこと
     * @return サーフェス
     */
    public Object getSurface() {
        synchronized (mLock) {
            if (mSurface == null) {
                createSurface();
            }

            pxLog("getVideoSurface: surface=" + mSurface);
            return mSurface;
        }
    }

    /**
     * 各種破棄
     */
    public void destroy() {
        synchronized (mLock) {
            if (mSurface != null) {
                mSurface.release();
                mSurface = null;
            }
            if (mSurTex != null) {
                mSurTex.release();
                mSurTex = null;
            }
            if (mTex != 0) {
                int[] textures = new int[1];
                textures[0] = mTex;
                GLES20.glDeleteTextures(1, textures, 0);
                mTex = 0;
            }
            if (mFrameBuf != 0) {
                int[] framebuffers = new int[1];
                framebuffers[0] = mFrameBuf;
                GLES20.glDeleteFramebuffers(1, framebuffers, 0);
                mFrameBuf = 0;
            }
            if (mProgram != 0) {
                GLES20.glDeleteProgram(mProgram);
                mProgram = 0;
            }

            mIsUpdated = false;
        }
    }

    /**
     * ビデオテクスチャ更新
     */
    public void updateVideoTexture() {
        synchronized (mLock) {
            if (!mIsUpdated) {
                System.out.println("not updated");
                return;
            }

            if (mFrameBuf == 0 || mUnityTex == 0 || mSurTex == null) {
                System.out.println("something is null"
                                + String.valueOf(mFrameBuf) + ","
                                + String.valueOf(mUnityTex) + ","
                                + String.valueOf(mSurTex)
                );

                return;
            }

            savePrevState();

            // Set up FBO
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuf);
            checkGLError("glBindFramebuffer");
            GLES20.glViewport(0, 0, mUnityTexWidth, mUnityTexHeight);
            checkGLError("glViewport");
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

            // load shader
            if (mProgram == 0) {
                if (!loadShader()) {
                    System.out.println("failed loadShader");
                    return;
                }
            }

            // Set up the program
            GLES20.glUseProgram(mProgram);
            checkGLError("glUseProgram : mProgram=" + mProgram);
            GLES20.glUniform1i(mTexture, 0);
            checkGLError("glUniform1i");

            // clear the scene
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            checkGLError("glClearColor");
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            checkGLError("glClear");

            // Bind and update texture image
            mSurTex.updateTexImage();

            // Update attribute values
            GLES20.glEnableVertexAttribArray(mPosition);
            checkGLError("glEnableVertexAttribArray : position");
            GLES20.glVertexAttribPointer(mPosition, 2, GLES20.GL_FLOAT, false, 0, mVertexBuffer);
            checkGLError("glVertexAttribPointer : position");
            GLES20.glEnableVertexAttribArray(mTexcoord);
            checkGLError("glEnableVertexAttribArray : texcoord");
            GLES20.glVertexAttribPointer(mTexcoord, 2, GLES20.GL_FLOAT, false, 0, mTexcoordBuffer);
            checkGLError("glVertexAttribPointer : texcoord");

            // Draw the quad
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            checkGLError("glDrawArrays");

            /* これ呼ぶと反映されない
            GLES20.glDisableVertexAttribArray(mPosition);
            checkGLError("glDisableVertexAttribArray : position");
            GLES20.glDisableVertexAttribArray(mTexcoord);
            checkGLError("glDisableVertexAttribArray : texcoord");
            */

            restorePrevState();

            mIsUpdated = false;
        }
    }

    /**
     * Surface フレーム更新通知
     */
    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        //pxLog("onFrameAvailable");
        synchronized (mLock) {
            mIsUpdated = true;
        }
    }

    // 過去状態保存
    private int[] mPrevTex = new int[1];
    private int[] mPrevFBO = new int[1];
    private int[] mPrevRenderBuffer = new int[1];
    private int[] mPrevProgram = new int[1];
    private int[] mPrevViewPort = new int[4];

    private int[] mArrayBuffer = new int[1];
    private int[] mElementArrayBuffer = new int[1];

    /**
     * 過去の状態を保存する
     */
    private void savePrevState() {
        GLES20.glGetIntegerv(GLES20.GL_ARRAY_BUFFER_BINDING, mArrayBuffer, 0);
        GLES20.glGetIntegerv(GLES20.GL_ELEMENT_ARRAY_BUFFER_BINDING, mElementArrayBuffer, 0);

        GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, mPrevTex, 0);
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, mPrevFBO, 0);
        GLES20.glGetIntegerv(GLES20.GL_RENDERBUFFER_BINDING, mPrevRenderBuffer, 0);
        GLES20.glGetIntegerv(GLES20.GL_CURRENT_PROGRAM, mPrevProgram, 0);
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, mPrevViewPort, 0);
    }

    /**
     * 過去の状態に戻す
     */
    private void restorePrevState() {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mArrayBuffer[0]);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mElementArrayBuffer[0]);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mPrevFBO[0]);
        checkGLError("glBindFramebuffer : prev");
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, mPrevRenderBuffer[0]);
        checkGLError("glBindRenderbuffer : prev");
        GLES20.glUseProgram(mPrevProgram[0]);
        checkGLError("glUseProgram : prev");
        GLES20.glViewport(mPrevViewPort[0], mPrevViewPort[1], mPrevViewPort[2], mPrevViewPort[3]);
        checkGLError("glViewport : prev");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mPrevTex[0]);
        checkGLError("glBindTexture : prev");
    }

    /**
     * サーフェス生成
     */
    private void createSurface() {
        synchronized (mLock) {
            // テクスチャ生成
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            mTex = textures[0];

            mSurTex = new SurfaceTexture(mTex);
            mSurTex.setOnFrameAvailableListener(this);

            mSurface = new Surface(mSurTex);

            mIsUpdated = false;
        }
        //pxLog("createSurface: tex=" + mTex + ", surfaceTexture=" + mSurTex + ", surface=" + mSurface);
    }

    /**
     * フレームバッファ生成
     * @param unity_texture
     * @param texture_width
     * @param texture_hegiht
     * @return
     */
    private boolean createFBO() {
        // フレームバッファ生成
        pxLog("createFBO()->" + String.valueOf(mFrameBuf));
        if (mFrameBuf == 0) {
            int[] framebuffers = new int[1];
            GLES20.glGenFramebuffers(1, framebuffers, 0);
            checkGLError("glGenFramebuffers");
            mFrameBuf = framebuffers[0];
        }
        pxLog("createFBO()->" + String.valueOf(mFrameBuf));

        // bind FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuf);
        checkGLError("glBindFramebuffer");

        // bind the output texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mUnityTex);
        checkGLError("glBindTexture");

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        checkGLError("");

        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, 0, 0);
        checkGLError("glFramebufferTexture2D : 0");
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, mUnityTex, 0);
        checkGLError("glFramebufferTexture2D : unity texture");

        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            pxLog("UNITY PLUGIN CANNOT MAKE VALID FBO ATTACHMENT FROM UNITY TEXTURE ID");
            return false;
        }

        return true;
    }

    // Vertexシェーダーコード
    private String vertexShaderCode =
        "attribute vec4 position;" +
        "attribute mediump vec4 textureCoordinate;" +
        "varying mediump vec2 coordinate;" +
        "void main() {" +
            "gl_Position = position;" +
            "coordinate = textureCoordinate.xy;" +
        "}";

    // Fragmentシェーダーコード
    private String fragmentShaderCode =
        "#extension GL_OES_EGL_image_external : require\n" +
        "varying highp vec2 coordinate;" +
        "uniform samplerExternalOES texture;" +
        "void main() {" +
            "gl_FragColor = texture2D(texture, coordinate);" +
        "}";

    private static final float kVertices[] = {
        -1.0f, -1.0f,
        1.0f, -1.0f,
        -1.0f,  1.0f,
        1.0f,  1.0f,
    };

    private static final float kTexcoords[] = {
        0.0f, 0.0f,
        1.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f,
    };

    private int mProgram = 0;
    private FloatBuffer mVertexBuffer = null;
    private FloatBuffer mTexcoordBuffer;
    private int mPosition = 0;
    private int mTexcoord = 0;
    private int mTexture = 0;

    /**
     * シェーダロード
     * @return true: 成功, false: 失敗
     */
    private boolean loadShader() {
        if (mProgram != 0) {
            return true;
        }

        GLES20.glUseProgram(0);

        // Programを作成
        mProgram = GLES20.glCreateProgram();
        checkGLError("glCreateProgram");
        if (mProgram == 0) {
            pxLog("Failed to create program");
            return false;
        }

        int[] status = new int[1];

        int vshader;
        {
            // Vertexシェーダーのコードをコンパイル
            vshader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
            checkGLError("glCreateShader : vert");
            GLES20.glShaderSource(vshader, vertexShaderCode);
            checkGLError("glShaderSource : vert");
            GLES20.glCompileShader(vshader);
            checkGLError("glCompileShader : vert");
            GLES20.glGetShaderiv(vshader, GLES20.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                pxLog("Failed to compile vertex shader");
                GLES20.glDeleteShader(vshader);
                vshader = 0;
            }
        }

        int fshader;
        {
            // Fragmentシェーダーのコードをコンパイル
            fshader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
            checkGLError("glCreateShader : frag");
            GLES20.glShaderSource(fshader, fragmentShaderCode);
            checkGLError("glShaderSource : frag");
            GLES20.glCompileShader(fshader);
            checkGLError("glCompileShader : frag");
            GLES20.glGetShaderiv(fshader, GLES20.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                pxLog("Failed to compile fragment shader");
                GLES20.glDeleteShader(fshader);
                fshader = 0;
                return false;
            }
        }

        // Programのシェーダーを設定
        GLES20.glAttachShader(mProgram, vshader);
        checkGLError("glAttachShader : vert");
        GLES20.glAttachShader(mProgram, fshader);
        checkGLError("glAttachShader : frag");

        // Link program
        GLES20.glLinkProgram(mProgram);
        checkGLError("glLinkProgram");
        GLES20.glGetProgramiv(mProgram, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            pxLog("Failed to link program");
            if (vshader != 0) GLES20.glDeleteShader(vshader);
            if (fshader != 0) GLES20.glDeleteShader(fshader);
            GLES20.glDeleteProgram(mProgram);
            mProgram = 0;
            return false;
        }

        mPosition = GLES20.glGetAttribLocation(mProgram, "position");
        checkGLError("glGetAttribLocation : position");
        mTexcoord = GLES20.glGetAttribLocation(mProgram, "textureCoordinate");
        checkGLError("glGetAttribLocation : texcoord");
        mTexture = GLES20.glGetUniformLocation(mProgram, "texture");
        checkGLError("glGetUniformLocation : texture");
        pxLog("mPosition=" + mPosition + ", mTexcoord=" + mTexcoord + ", mTexture=" + mTexture);

        if (vshader != 0) {
            GLES20.glDetachShader(mProgram, vshader);
            GLES20.glDeleteShader(vshader);
        }
        if (fshader != 0) {
            GLES20.glDetachShader(mProgram, fshader);
            GLES20.glDeleteShader(fshader);
        }

        mVertexBuffer = ByteBuffer.allocateDirect(kVertices.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mVertexBuffer.put(kVertices).position(0);
        mTexcoordBuffer = ByteBuffer.allocateDirect(kTexcoords.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTexcoordBuffer.put(kTexcoords).position(0);

        return true;
    }

    /**
     * GL Errorチェック
     * 
     * @param op
     *            エラーの場合に出力する文字列
     */
    private void checkGLError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
//            Log.e(TAG, op + ": glError " + error);
            System.out.println("glError:" + String.valueOf(error));
        }
    }

    /**
     * ログ出力
     * 
     * @param message
     *            メッセージ文字列
     * @param formatArgs
     *            フォーマット引数
     */
    private void pxLog(final String message) {
//        Log.d(TAG, message);
        System.out.println(message);
    }
}
