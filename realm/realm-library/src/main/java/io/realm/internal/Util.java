/*
 * Copyright 2014 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.internal;

import android.os.Build;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nullable;

import io.realm.RealmConfiguration;
import io.realm.RealmModel;
import io.realm.RealmObject;
import io.realm.internal.android.AndroidCapabilities;
import io.realm.log.RealmLog;


public class Util {

    private static Boolean rxJavaAvailable;

    public static String getTablePrefix() {
        return nativeGetTablePrefix();
    }

    static native String nativeGetTablePrefix();

    /**
     * Normalizes a input class to it's original RealmObject class so it is transparent whether or not the input class
     * was a RealmProxy class.
     */
    public static Class<? extends RealmModel> getOriginalModelClass(Class<? extends RealmModel> clazz) {

        // The compiler would allow these classes to be passed as arguments, but they are never
        // valid as a Realm model class
        if (clazz.equals(RealmModel.class) || clazz.equals(RealmObject.class)) {
            throw new IllegalArgumentException("RealmModel or RealmObject was passed as an argument. " +
                    "Only subclasses of these can be used as arguments to methods that accept a Realm model class.");
        }

        // This cast is correct because 'clazz' is either the type
        // generated by RealmProxy or the original type extending directly from RealmObject.
        @SuppressWarnings("unchecked")
        Class<? extends RealmModel> superclass = (Class<? extends RealmModel>) clazz.getSuperclass();

        if (!superclass.equals(Object.class) && !superclass.equals(RealmObject.class)) {
            clazz = superclass;
        }

        return clazz;
    }

    //-----------------------------------------------------------------------

    /**
     * <p>Gets the stack trace from a Throwable as a String.</p>
     * <p>
     * <p>The result of this method vary by JDK version as this method
     * uses {@link Throwable#printStackTrace(java.io.PrintWriter)}.
     * On JDK1.3 and earlier, the cause exception will not be shown
     * unless the specified throwable alters printStackTrace.</p>
     *
     * @param throwable the <code>Throwable</code> to be examined
     * @return the stack trace as generated by the exception's
     * <code>printStackTrace(PrintWriter)</code> method
     * <p>
     * Credit: https://commons.apache.org/proper/commons-lang/apidocs/src-html/org/apache/commons/lang3/exception/ExceptionUtils.html
     */
    public static String getStackTrace(final Throwable throwable) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw);
        return sw.getBuffer().toString();
    }

    // Credit: http://stackoverflow.com/questions/2799097/how-can-i-detect-when-an-android-application-is-running-in-the-emulator
    public static boolean isEmulator() {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT);
    }

    public static boolean isEmptyString(@Nullable String str) {
        return str == null || str.length() == 0;
    }

    /**
     * To delete Realm and related temporary files. This must be called in
     * {@link OsObjectStore#callWithLock(RealmConfiguration, Runnable)}'s callback.
     *
     * @return {@code true} if the realm file is deleted. Temporary file deletion failure will not impact the return
     * value, instead, a warning will be logged.
     */
    public static boolean deleteRealm(String canonicalPath, File realmFolder, String realmFileName) {
        final String management = ".management";
        File managementFolder = new File(realmFolder, realmFileName + management);
        File realmFile = new File(canonicalPath);
        // This file is not always stored here, but if it is we want to delete it.
        // If it isn't found it is placed in a temporary folder, so no reason to delete it.
        File fifoFile = new File(canonicalPath + ".note");

        // Deletes files in management directory and the directory.
        // There is no subfolders in the management directory.
        File[] files = managementFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                boolean deleteResult = file.delete();
                if (!deleteResult) {
                    RealmLog.warn( String.format(Locale.ENGLISH,"Realm temporary file at %s cannot be deleted",
                            file.getAbsolutePath()));
                }
            }
        }
        if (managementFolder.exists() && !managementFolder.delete()) {
            RealmLog.warn( String.format(Locale.ENGLISH,"Realm temporary folder at %s cannot be deleted",
                    managementFolder.getAbsolutePath()));
        }

        boolean realmDeleted;
        if (realmFile.exists()) {
            realmDeleted = realmFile.delete();
            if (!realmDeleted) {
                RealmLog.warn(String.format(Locale.ENGLISH,"Realm file at %s cannot be deleted",
                        realmFile.getAbsolutePath()));
            }
        } else {
            realmDeleted = true;
        }

        if (fifoFile.exists() && !fifoFile.delete()) {
            RealmLog.warn(String.format(Locale.ENGLISH,".note file at %s cannot be deleted",
                        fifoFile.getAbsolutePath()));
        }

        return realmDeleted;
    }

    /**
     * Converts a var arg argument list to a set ignoring any duplicates and null values.
     */
    public static <T> Set<T> toSet(T... items) {
        //noinspection ConstantConditions
        if (items == null) {
            return Collections.emptySet();
        } else {
            Set<T> set = new LinkedHashSet<>();
            for (int i = 0; i < items.length; i++) {
                T item = items[i];
                if (item != null) {
                    set.add(item);
                }
            }
            return set;
        }
    }

    public static void checkEmpty(String argValue, String argName) {
        if (isEmptyString(argValue)) {
            throw new IllegalArgumentException("Non-empty '" + argName + "' required.");
        }
    }

    public static void checkNull(@Nullable Object argValue, String argName) {
        if (argValue == null) {
            throw new IllegalArgumentException("Nonnull '" + argName + "' required.");
        }
    }

    public static void checkLooperThread(String errorMessage) {
        AndroidCapabilities capabilities = new AndroidCapabilities();
        capabilities.checkCanDeliverNotification(errorMessage);
    }

    public static void checkNotOnMainThread(String errorMessage) {
        if (new AndroidCapabilities().isMainThread()) {
            throw new IllegalStateException(errorMessage);
        }
    }

    /**
     * Checks if RxJava is can be loaded.
     *
     * @return {@code true} if RxJava dependency exist, {@code false} otherwise.
     */
    @SuppressWarnings("LiteralClassName")
    public static synchronized boolean isRxJavaAvailable() {
        if (rxJavaAvailable == null) {
            try {
                Class.forName("io.reactivex.Flowable");
                rxJavaAvailable = true;
            } catch (ClassNotFoundException ignore) {
                rxJavaAvailable = false;
            }
        }
        return rxJavaAvailable;
    }

}
