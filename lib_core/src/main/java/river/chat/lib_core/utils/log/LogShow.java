package river.chat.lib_core.utils.log;

import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.RequiresApi;
import androidx.collection.SimpleArrayMap;
import river.chat.lib_core.app.BaseApplication;

/**
 * @Author :rickBei
 * @Date :2019/7/11 13:14
 * @Descripe:
 **/
public class LogShow {

    public static final int V = Log.VERBOSE;
    public static final int D = Log.DEBUG;
    public static final int I = Log.INFO;
    public static final int W = Log.WARN;
    public static final int E = Log.ERROR;
    public static final int A = Log.ASSERT;

    @IntDef({V, D, I, W, E, A})
    @Retention(RetentionPolicy.SOURCE)
    public @interface TYPE {
    }

    private static final char[] T = new char[]{'V', 'D', 'I', 'W', 'E', 'A'};

    private static final int FILE = 0x10;

    private static final String LINE_SEP = System.getProperty("line.separator");
    private static final String TOP_CORNER = "┌";
    private static final String MIDDLE_CORNER = "├";
    private static final String LEFT_BORDER = "│ ";
    private static final String BOTTOM_CORNER = "└";
    private static final String SIDE_DIVIDER = "────────────────────────────────────────────────────────";
    private static final String MIDDLE_DIVIDER = "┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄";
    private static final String TOP_BORDER = TOP_CORNER + SIDE_DIVIDER + SIDE_DIVIDER;
    private static final String MIDDLE_BORDER = MIDDLE_CORNER + MIDDLE_DIVIDER + MIDDLE_DIVIDER;
    private static final String BOTTOM_BORDER = BOTTOM_CORNER + SIDE_DIVIDER + SIDE_DIVIDER;
    private static final int MAX_LEN = 3000;
    private static final String NOTHING = "log nothing";
    private static final String NULL = "null";
    private static final String ARGS = "args";
    private static final String PLACEHOLDER = " ";
    private static final LogConfig LOG_CONFIG = new LogConfig();

    private static final ThreadLocal<SimpleDateFormat> SDF_THREAD_LOCAL = new ThreadLocal<>();

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private static final SimpleArrayMap<Class, IFormatter> I_FORMATTER_MAP = new SimpleArrayMap<>();

    public LogShow() {

    }

    public static void d(final Object... contents) {
        log(D, LOG_CONFIG.getGlobalTag(), contents);
    }

    public static void v(final Object... contents) {
        log(V, LOG_CONFIG.getGlobalTag(), contents);
    }


    public static void i(final Object... contents) {
        log(I, LOG_CONFIG.getGlobalTag(), contents);
    }


    public static void e(final Object... contents) {
        log(E, LOG_CONFIG.getGlobalTag(), contents);
    }

    public static void a(final Object... contents) {
        log(A, LOG_CONFIG.getGlobalTag(), contents);
    }

    public static void file(final Object content) {
        log(FILE | D, LOG_CONFIG.getGlobalTag(), content);
    }


    public static void init(boolean isDebug, boolean isWrite2File) {
        LOG_CONFIG.setLogSwitch(isDebug);
    }

    public static void log(final int type, final String tag, final Object... contents) {
        if (!LOG_CONFIG.isLogSwitch()) return;
        int type_low = type & 0x0f, type_high = type & 0xf0;
        if (LOG_CONFIG.isLog2ConsoleSwitch() || type_high == FILE) {
            if (type_low < LOG_CONFIG.mConsoleFilter && type_low < LOG_CONFIG.mFileFilter) return;
            final TagHead tagHead = processTagAndHead(tag);
            final String body = processBody(type_high, contents);
            if (LOG_CONFIG.isLog2ConsoleSwitch() && type_high != FILE && type_low >= LOG_CONFIG.mConsoleFilter) {
                print2Console(type_low, tagHead.tag, tagHead.consoleHead, body);
            }

        }
    }

    private static TagHead processTagAndHead(String tag) {
        if (!LOG_CONFIG.mTagIsSpace && !LOG_CONFIG.isLogHeadSwitch()) {
            tag = LOG_CONFIG.getGlobalTag();
        } else {
            final StackTraceElement[] stackTrace = new Throwable().getStackTrace();
            final int stackIndex = 3 + LOG_CONFIG.getStackOffset();
            if (stackIndex >= stackTrace.length) {
                StackTraceElement targetElement = stackTrace[3];
                final String fileName = getFileName(targetElement);
                if (LOG_CONFIG.mTagIsSpace && isSpace(tag)) {
                    int index = fileName.indexOf('.');// Use proguard may not find '.'.
                    tag = index == -1 ? fileName : fileName.substring(0, index);
                }
                return new TagHead(tag, null, ": ");
            }
            StackTraceElement targetElement = stackTrace[stackIndex];
            final String fileName = getFileName(targetElement);
            if (LOG_CONFIG.mTagIsSpace && isSpace(tag)) {
                int index = fileName.indexOf('.');// Use proguard may not find '.'.
                tag = index == -1 ? fileName : fileName.substring(0, index);
            }
            if (LOG_CONFIG.isLogHeadSwitch()) {
                String tName = Thread.currentThread().getName();
                final String head = new Formatter()
                        .format("%s, %s.%s(%s:%d)",
                                tName,
                                targetElement.getClassName(),
                                targetElement.getMethodName(),
                                fileName,
                                targetElement.getLineNumber())
                        .toString();
                final String fileHead = " [" + head + "]: ";
                if (LOG_CONFIG.getStackDeep() <= 1) {
                    return new TagHead(tag, new String[]{head}, fileHead);
                } else {
                    final String[] consoleHead =
                            new String[Math.min(
                                    LOG_CONFIG.getStackDeep(),
                                    stackTrace.length - stackIndex
                            )];
                    consoleHead[0] = head;
                    int spaceLen = tName.length() + 2;
                    String space = new Formatter().format("%" + spaceLen + "s", "").toString();
                    for (int i = 1, len = consoleHead.length; i < len; ++i) {
                        targetElement = stackTrace[i + stackIndex];
                        consoleHead[i] = new Formatter()
                                .format("%s%s.%s(%s:%d)",
                                        space,
                                        targetElement.getClassName(),
                                        targetElement.getMethodName(),
                                        getFileName(targetElement),
                                        targetElement.getLineNumber())
                                .toString();
                    }
                    return new TagHead(tag, consoleHead, fileHead);
                }
            }
        }
        return new TagHead(tag, null, ": ");
    }

    private static String getFileName(final StackTraceElement targetElement) {
        String fileName = targetElement.getFileName();
        if (fileName != null) return fileName;
        // If name of file is null, should add
        // "-keepattributes SourceFile,LineNumberTable" in proguard file.
        String className = targetElement.getClassName();
        String[] classNameInfo = className.split("\\.");
        if (classNameInfo.length > 0) {
            className = classNameInfo[classNameInfo.length - 1];
        }
        int index = className.indexOf('$');
        if (index != -1) {
            className = className.substring(0, index);
        }
        return className + ".java";
    }

    private static String processBody(final int type, final Object... contents) {
        String body = NULL;
        if (contents != null) {
            if (contents.length == 1) {
                body = formatObject(type, contents[0]);
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0, len = contents.length; i < len; ++i) {
                    Object content = contents[i];
                    sb.append(ARGS)
                            .append("[")
                            .append(i)
                            .append("]")
                            .append(" = ")
                            .append(formatObject(content))
                            .append(LINE_SEP);
                }
                body = sb.toString();
            }
        }
        return body.length() == 0 ? NOTHING : body;
    }

    private static String formatObject(int type, Object object) {
        if (object == null) return NULL;
        return formatObject(object);
    }

    private static String formatObject(Object object) {
        if (object == null) return NULL;
        if (!I_FORMATTER_MAP.isEmpty()) {
            IFormatter iFormatter = I_FORMATTER_MAP.get(getClassFromObject(object));
            if (iFormatter != null) {
                //noinspection unchecked
                return iFormatter.format(object);
            }
        }
        return LogFormatter.object2String(object);
    }

    private static void print2Console(final int type,
                                      final String tag,
                                      final String[] head,
                                      final String msg) {
        if (LOG_CONFIG.isSingleTagSwitch()) {
            printSingleTagMsg(type, tag, processSingleTagMsg(type, tag, head, msg));
        } else {
            printBorder(type, tag, true);
            printHead(type, tag, head);
            printMsg(type, tag, msg);
            printBorder(type, tag, false);
        }
    }

    private static void printBorder(final int type, final String tag, boolean isTop) {
        if (LOG_CONFIG.isLogBorderSwitch()) {
            Log.println(type, tag, isTop ? TOP_BORDER : BOTTOM_BORDER);
        }
    }

    private static void printHead(final int type, final String tag, final String[] head) {
        if (head != null) {
            for (String aHead : head) {
                Log.println(type, tag, LOG_CONFIG.isLogBorderSwitch() ? LEFT_BORDER + aHead : aHead);
            }
            if (LOG_CONFIG.isLogBorderSwitch()) Log.println(type, tag, MIDDLE_BORDER);
        }
    }

    private static void printMsg(final int type, final String tag, final String msg) {
        int len = msg.length();
        int countOfSub = len / MAX_LEN;
        if (countOfSub > 0) {
            int index = 0;
            for (int i = 0; i < countOfSub; i++) {
                printSubMsg(type, tag, msg.substring(index, index + MAX_LEN));
                index += MAX_LEN;
            }
            if (index != len) {
                printSubMsg(type, tag, msg.substring(index, len));
            }
        } else {
            printSubMsg(type, tag, msg);
        }
    }

    private static void printSubMsg(final int type, final String tag, final String msg) {
        if (!LOG_CONFIG.isLogBorderSwitch()) {
            Log.println(type, tag, msg);
            return;
        }
        StringBuilder sb = new StringBuilder();
        String[] lines = msg.split(LINE_SEP);
        for (String line : lines) {
            Log.println(type, tag, LEFT_BORDER + line);
        }
    }

    private static String processSingleTagMsg(final int type,
                                              final String tag,
                                              final String[] head,
                                              final String msg) {
        StringBuilder sb = new StringBuilder();
        sb.append(PLACEHOLDER).append(LINE_SEP);
        if (LOG_CONFIG.isLogBorderSwitch()) {
            sb.append(TOP_BORDER).append(LINE_SEP);
            if (head != null) {
                for (String aHead : head) {
                    sb.append(LEFT_BORDER).append(aHead).append(LINE_SEP);
                }
                sb.append(MIDDLE_BORDER).append(LINE_SEP);
            }
            for (String line : msg.split(LINE_SEP)) {
                sb.append(LEFT_BORDER).append(line).append(LINE_SEP);
            }
            sb.append(BOTTOM_BORDER);
        } else {
            if (head != null) {
                for (String aHead : head) {
                    sb.append(aHead).append(LINE_SEP);
                }
            }
            sb.append(msg);
        }
        return sb.toString();
    }


    private static void printSingleTagMsg(final int type, final String tag, final String msg) {
        int len = msg.length();
        int countOfSub = len / MAX_LEN;
        if (countOfSub > 0) {
            if (LOG_CONFIG.isLogBorderSwitch()) {
                Log.println(type, tag, msg.substring(0, MAX_LEN) + LINE_SEP + BOTTOM_BORDER);
                int index = MAX_LEN;
                for (int i = 1; i < countOfSub; i++) {
                    Log.println(type, tag, PLACEHOLDER + LINE_SEP + TOP_BORDER + LINE_SEP
                            + LEFT_BORDER + msg.substring(index, index + MAX_LEN)
                            + LINE_SEP + BOTTOM_BORDER);
                    index += MAX_LEN;
                }
                if (index != len) {
                    Log.println(type, tag, PLACEHOLDER + LINE_SEP + TOP_BORDER + LINE_SEP
                            + LEFT_BORDER + msg.substring(index, len));
                }
            } else {
                Log.println(type, tag, msg.substring(0, MAX_LEN));
                int index = MAX_LEN;
                for (int i = 1; i < countOfSub; i++) {
                    Log.println(type, tag,
                            PLACEHOLDER + LINE_SEP + msg.substring(index, index + MAX_LEN));
                    index += MAX_LEN;
                }
                if (index != len) {
                    Log.println(type, tag, PLACEHOLDER + LINE_SEP + msg.substring(index, len));
                }
            }
        } else {
            Log.println(type, tag, msg);
        }
    }



    private static SimpleDateFormat getSdf() {
        SimpleDateFormat simpleDateFormat = SDF_THREAD_LOCAL.get();
        if (simpleDateFormat == null) {
            simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            SDF_THREAD_LOCAL.set(simpleDateFormat);
        }
        return simpleDateFormat;
    }

    private static void printDeviceInfo(final String filePath) {
        String versionName = "";
        int versionCode = 0;
        try {
            PackageInfo pi = BaseApplication.getInstance()
                    .getPackageManager()
                    .getPackageInfo(BaseApplication.getInstance().getPackageName(), 0);
            if (pi != null) {
                versionName = pi.versionName;
                versionCode = pi.versionCode;
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        String time = filePath.substring(filePath.length() - 14, filePath.length() - 4);
        final String head = "************* Log Head ****************" +
                "\nDate of Log        : " + time +
                "\nDevice Manufacturer: " + Build.MANUFACTURER +
                "\nDevice Model       : " + Build.MODEL +
                "\nAndroid Version    : " + Build.VERSION.RELEASE +
                "\nAndroid SDK        : " + Build.VERSION.SDK_INT +
                "\nApp VersionName    : " + versionName +
                "\nApp VersionCode    : " + versionCode +
                "\n************* Log Head ****************\n\n";
        input2File(head, filePath);
    }

    private static boolean createOrExistsDir(final File file) {
        return file != null && (file.exists() ? file.isDirectory() : file.mkdirs());
    }

    private static boolean isSpace(final String s) {
        if (s == null) return true;
        for (int i = 0, len = s.length(); i < len; ++i) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static void input2File(final String input, final String filePath) {
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                BufferedWriter bw = null;
                try {
                    bw = new BufferedWriter(new FileWriter(filePath, true));
                    bw.write(input);
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e("LogUtil", "log to " + filePath + " failed!");
                } finally {
                    try {
                        if (bw != null) {
                            bw.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public static class LogConfig {
        private boolean mLogSwitch = true;  // The switch of log.
        private boolean mLog2ConsoleSwitch = true;  // The logcat's switch of log.
        private String mGlobalTag = "";    // The global tag of log.
        private boolean mTagIsSpace = true;  // The global tag is space.
        private boolean mLogHeadSwitch = true;  // The head's switch of log.
        private boolean mLogBorderSwitch = true;  // The border's switch of log.
        private boolean mSingleTagSwitch = true;  // The single tag of log.
        private int mConsoleFilter = V;     // The console's filter of log.
        private int mFileFilter = V;     // The file's filter of log.
        private int mStackDeep = 1;     // The stack's deep of log.
        private int mStackOffset = 0;     // The stack's offset of log.
        private int mSaveDays = -1;    // The save days of log.
        private String mProcessName = getCurrentProcessName();

        private LogConfig() {
        }

        public LogConfig setLogSwitch(final boolean logSwitch) {
            mLogSwitch = logSwitch;
            return this;
        }

        public LogConfig setConsoleSwitch(final boolean consoleSwitch) {
            mLog2ConsoleSwitch = consoleSwitch;
            return this;
        }

        public LogConfig setGlobalTag(final String tag) {
            if (isSpace(tag)) {
                mGlobalTag = "";
                mTagIsSpace = true;
            } else {
                mGlobalTag = tag;
                mTagIsSpace = false;
            }
            return this;
        }

        public LogConfig setLogHeadSwitch(final boolean logHeadSwitch) {
            mLogHeadSwitch = logHeadSwitch;
            return this;
        }


        public LogConfig setBorderSwitch(final boolean borderSwitch) {
            mLogBorderSwitch = borderSwitch;
            return this;
        }

        public LogConfig setSingleTagSwitch(final boolean singleTagSwitch) {
            mSingleTagSwitch = singleTagSwitch;
            return this;
        }

        public LogConfig setConsoleFilter(@TYPE final int consoleFilter) {
            mConsoleFilter = consoleFilter;
            return this;
        }

        public LogConfig setFileFilter(@TYPE final int fileFilter) {
            mFileFilter = fileFilter;
            return this;
        }

        public LogConfig setStackDeep(@IntRange(from = 1) final int stackDeep) {
            mStackDeep = stackDeep;
            return this;
        }

        public LogConfig setStackOffset(@IntRange(from = 0) final int stackOffset) {
            mStackOffset = stackOffset;
            return this;
        }

        public LogConfig setSaveDays(@IntRange(from = 1) final int saveDays) {
            mSaveDays = saveDays;
            return this;
        }

        public final <T> LogConfig addFormatter(final IFormatter<T> iFormatter) {
            if (iFormatter != null) {
                I_FORMATTER_MAP.put(getTypeClassFromParadigm(iFormatter), iFormatter);
            }
            return this;
        }

        public String getProcessName() {
            return mProcessName;
        }

        public boolean isLogSwitch() {
            return mLogSwitch;
        }

        public boolean isLog2ConsoleSwitch() {
            return mLog2ConsoleSwitch;
        }

        public String getGlobalTag() {
            if (isSpace(mGlobalTag)) return "";
            return mGlobalTag;
        }

        public boolean isLogHeadSwitch() {
            return mLogHeadSwitch;
        }

        public boolean isLogBorderSwitch() {
            return mLogBorderSwitch;
        }

        public boolean isSingleTagSwitch() {
            return mSingleTagSwitch;
        }

        public char getConsoleFilter() {
            return T[mConsoleFilter - V];
        }

        public char getFileFilter() {
            return T[mFileFilter - V];
        }

        public int getStackDeep() {
            return mStackDeep;
        }

        public int getStackOffset() {
            return mStackOffset;
        }

        public int getSaveDays() {
            return mSaveDays;
        }

        private static String getCurrentProcessName() {
            if(BaseApplication.getInstance() == null)
                return "";
            ActivityManager am = (ActivityManager) BaseApplication.getInstance().getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return "";
            List<ActivityManager.RunningAppProcessInfo> info = am.getRunningAppProcesses();
            if (info == null || info.size() == 0) return "";
            int pid = android.os.Process.myPid();
            for (ActivityManager.RunningAppProcessInfo aInfo : info) {
                if (aInfo.pid == pid) {
                    if (aInfo.processName != null) {
                        return aInfo.processName;
                    }
                }
            }
            return "";
        }

        @Override
        public String toString() {
            return "process: " + getProcessName()
                    + LINE_SEP + "switch: " + isLogSwitch()
                    + LINE_SEP + "console: " + isLog2ConsoleSwitch()
                    + LINE_SEP + "tag: " + getGlobalTag()
                    + LINE_SEP + "head: " + isLogHeadSwitch()
                    + LINE_SEP + "border: " + isLogBorderSwitch()
                    + LINE_SEP + "singleTag: " + isSingleTagSwitch()
                    + LINE_SEP + "consoleFilter: " + getConsoleFilter()
                    + LINE_SEP + "fileFilter: " + getFileFilter()
                    + LINE_SEP + "stackDeep: " + getStackDeep()
                    + LINE_SEP + "stackOffset: " + getStackOffset()
                    + LINE_SEP + "saveDays: " + getSaveDays()
                    + LINE_SEP + "formatter: " + I_FORMATTER_MAP;
        }
    }

    public abstract static class IFormatter<T> {
        public abstract String format(T t);
    }

    private static class TagHead {
        String tag;
        String[] consoleHead;
        String fileHead;

        TagHead(String tag, String[] consoleHead, String fileHead) {
            this.tag = tag;
            this.consoleHead = consoleHead;
            this.fileHead = fileHead;
        }
    }

    private static class LogFormatter {

        static String object2String(Object object) {
            if (object.getClass().isArray()) return array2String(object);
            if (object instanceof Throwable) return throwable2String((Throwable) object);
            if (object instanceof Bundle) return bundle2String((Bundle) object);
            if (object instanceof Intent) return intent2String((Intent) object);
            return object.toString();
        }

        private static String throwable2String(final Throwable e) {
            return e.getMessage();
        }

        private static String bundle2String(Bundle bundle) {
            Iterator<String> iterator = bundle.keySet().iterator();
            if (!iterator.hasNext()) {
                return "Bundle {}";
            }
            StringBuilder sb = new StringBuilder(128);
            sb.append("Bundle { ");
            for (; ; ) {
                String key = iterator.next();
                Object value = bundle.get(key);
                sb.append(key).append('=');
                if (value instanceof Bundle) {
                    sb.append(value == bundle ? "(this Bundle)" : bundle2String((Bundle) value));
                } else {
                    sb.append(formatObject(value));
                }
                if (!iterator.hasNext()) return sb.append(" }").toString();
                sb.append(',').append(' ');
            }
        }

        private static String intent2String(Intent intent) {
            StringBuilder sb = new StringBuilder(128);
            sb.append("Intent { ");
            boolean first = true;
            String mAction = intent.getAction();
            if (mAction != null) {
                sb.append("act=").append(mAction);
                first = false;
            }
            Set<String> mCategories = intent.getCategories();
            if (mCategories != null) {
                if (!first) {
                    sb.append(' ');
                }
                first = false;
                sb.append("cat=[");
                boolean firstCategory = true;
                for (String c : mCategories) {
                    if (!firstCategory) {
                        sb.append(',');
                    }
                    sb.append(c);
                    firstCategory = false;
                }
                sb.append("]");
            }
            Uri mData = intent.getData();
            if (mData != null) {
                if (!first) {
                    sb.append(' ');
                }
                first = false;
                sb.append("dat=").append(mData);
            }
            String mType = intent.getType();
            if (mType != null) {
                if (!first) {
                    sb.append(' ');
                }
                first = false;
                sb.append("typ=").append(mType);
            }
            int mFlags = intent.getFlags();
            if (mFlags != 0) {
                if (!first) {
                    sb.append(' ');
                }
                first = false;
                sb.append("flg=0x").append(Integer.toHexString(mFlags));
            }
            String mPackage = intent.getPackage();
            if (mPackage != null) {
                if (!first) {
                    sb.append(' ');
                }
                first = false;
                sb.append("pkg=").append(mPackage);
            }
            ComponentName mComponent = intent.getComponent();
            if (mComponent != null) {
                if (!first) {
                    sb.append(' ');
                }
                first = false;
                sb.append("cmp=").append(mComponent.flattenToShortString());
            }
            Rect mSourceBounds = intent.getSourceBounds();
            if (mSourceBounds != null) {
                if (!first) {
                    sb.append(' ');
                }
                first = false;
                sb.append("bnds=").append(mSourceBounds.toShortString());
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                ClipData mClipData = intent.getClipData();
                if (mClipData != null) {
                    if (!first) {
                        sb.append(' ');
                    }
                    first = false;
                    clipData2String(mClipData, sb);
                }
            }
            Bundle mExtras = intent.getExtras();
            if (mExtras != null) {
                if (!first) {
                    sb.append(' ');
                }
                first = false;
                sb.append("extras={");
                sb.append(bundle2String(mExtras));
                sb.append('}');
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                Intent mSelector = intent.getSelector();
                if (mSelector != null) {
                    if (!first) {
                        sb.append(' ');
                    }
                    first = false;
                    sb.append("sel={");
                    sb.append(mSelector == intent ? "(this Intent)" : intent2String(mSelector));
                    sb.append("}");
                }
            }
            sb.append(" }");
            return sb.toString();
        }


        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
        private static void clipData2String(ClipData clipData, StringBuilder sb) {
            ClipData.Item item = clipData.getItemAt(0);
            if (item == null) {
                sb.append("ClipData.Item {}");
                return;
            }
            sb.append("ClipData.Item { ");
            String mHtmlText = item.getHtmlText();
            if (mHtmlText != null) {
                sb.append("H:");
                sb.append(mHtmlText);
                sb.append("}");
                return;
            }
            CharSequence mText = item.getText();
            if (mText != null) {
                sb.append("T:");
                sb.append(mText);
                sb.append("}");
                return;
            }
            Uri uri = item.getUri();
            if (uri != null) {
                sb.append("U:").append(uri);
                sb.append("}");
                return;
            }
            Intent intent = item.getIntent();
            if (intent != null) {
                sb.append("I:");
                sb.append(intent2String(intent));
                sb.append("}");
                return;
            }
            sb.append("NULL");
            sb.append("}");
        }

        private static String array2String(Object object) {
            if (object instanceof Object[]) {
                return Arrays.deepToString((Object[]) object);
            } else if (object instanceof boolean[]) {
                return Arrays.toString((boolean[]) object);
            } else if (object instanceof byte[]) {
                return Arrays.toString((byte[]) object);
            } else if (object instanceof char[]) {
                return Arrays.toString((char[]) object);
            } else if (object instanceof double[]) {
                return Arrays.toString((double[]) object);
            } else if (object instanceof float[]) {
                return Arrays.toString((float[]) object);
            } else if (object instanceof int[]) {
                return Arrays.toString((int[]) object);
            } else if (object instanceof long[]) {
                return Arrays.toString((long[]) object);
            } else if (object instanceof short[]) {
                return Arrays.toString((short[]) object);
            }
            throw new IllegalArgumentException("Array has incompatible type: " + object.getClass());
        }
    }

    private static <T> Class getTypeClassFromParadigm(final IFormatter<T> formatter) {
        Type[] genericInterfaces = formatter.getClass().getGenericInterfaces();
        Type type;
        if (genericInterfaces.length == 1) {
            type = genericInterfaces[0];
        } else {
            type = formatter.getClass().getGenericSuperclass();
        }
        type = ((ParameterizedType) type).getActualTypeArguments()[0];
        while (type instanceof ParameterizedType) {
            type = ((ParameterizedType) type).getRawType();
        }
        String className = type.toString();
        if (className.startsWith("class ")) {
            className = className.substring(6);
        } else if (className.startsWith("interface ")) {
            className = className.substring(10);
        }
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Class getClassFromObject(final Object obj) {
        Class objClass = obj.getClass();
        if (objClass.isAnonymousClass() || objClass.isSynthetic()) {
            Type[] genericInterfaces = objClass.getGenericInterfaces();
            String className;
            if (genericInterfaces.length == 1) {// interface
                Type type = genericInterfaces[0];
                while (type instanceof ParameterizedType) {
                    type = ((ParameterizedType) type).getRawType();
                }
                className = type.toString();
            } else {// abstract class or lambda
                Type type = objClass.getGenericSuperclass();
                while (type instanceof ParameterizedType) {
                    type = ((ParameterizedType) type).getRawType();
                }
                className = type.toString();
            }

            if (className.startsWith("class ")) {
                className = className.substring(6);
            } else if (className.startsWith("interface ")) {
                className = className.substring(10);
            }
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return objClass;
    }
}
