/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.nodes;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.NotLinkException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@GenerateUncached
@ImportStatic(PGuards.class)
public abstract class PRaiseOSErrorNode extends Node {

    private static final Pattern ERRNO_PATTERN = Pattern.compile("error=(\\d+)");

    public abstract PException execute(Frame frame, Object[] arguments);

    public final PException raiseOSError(Frame frame, OSErrorEnum oserror) {
        return execute(frame, new Object[]{oserror.getNumber(), oserror.getMessage()});
    }

    public final PException raiseOSError(Frame frame, OSErrorEnum oserror, Exception e) {
        return raiseOSError(frame, oserror, getMessage(e));
    }

    @TruffleBoundary
    private static String getMessage(Exception e) {
        return e.getMessage();
    }

    @TruffleBoundary
    private static String getReason(FileSystemException e) {
        return e.getReason();
    }

    @TruffleBoundary
    private static OSErrorEnum tryFindErrnoFromMessage(Exception e) {
        Matcher m = ERRNO_PATTERN.matcher(e.getMessage());
        if (m.find()) {
            return OSErrorEnum.fromNumber(Integer.parseInt(m.group(1)));
        }
        return null;
    }

    public final PException raiseOSError(Frame frame, OSErrorEnum oserror, String filename) {
        return execute(frame, new Object[]{oserror.getNumber(), oserror.getMessage(), filename});
    }

    public final PException raiseOSError(Frame frame, OSErrorEnum oserror, String filename, String filename2) {
        return execute(frame, new Object[]{oserror.getNumber(), oserror.getMessage(), filename, PNone.NONE, filename2});
    }

    public final PException raiseOSError(Frame frame, Exception e) {
        return raiseOSError(frame, e, null, null);
    }

    public final PException raiseOSError(Frame frame, Exception e, String filename) {
        return raiseOSError(frame, e, filename, null);
    }

    public final PException raiseOSError(Frame frame, Exception e, String filename, String filename2) {
        OSErrorEnum oserror;
        String message = null;
        if (e instanceof IOException) {
            if (e instanceof NoSuchFileException || e instanceof FileNotFoundException) {
                oserror = OSErrorEnum.ENOENT;
            } else if (e instanceof AccessDeniedException) {
                oserror = OSErrorEnum.EACCES;
            } else if (e instanceof FileAlreadyExistsException) {
                oserror = OSErrorEnum.EEXIST;
            } else if (e instanceof NotDirectoryException) {
                oserror = OSErrorEnum.ENOTDIR;
            } else if (e instanceof DirectoryNotEmptyException) {
                oserror = OSErrorEnum.ENOTEMPTY;
            } else if (e instanceof FileSystemLoopException) {
                oserror = OSErrorEnum.ELOOP;
            } else if (e instanceof NotLinkException) {
                oserror = OSErrorEnum.EINVAL;
            } else if (e instanceof ClosedChannelException) {
                oserror = OSErrorEnum.EPIPE;
            } else if (e instanceof FileSystemException) {
                String reason = getReason((FileSystemException) e);
                oserror = OSErrorEnum.fromMessage(reason);
                if (oserror == null) {
                    oserror = OSErrorEnum.EIO;
                    message = reason;
                }
            } else { // Generic IOException
                oserror = tryFindErrnoFromMessage(e);
                if (oserror == null) {
                    oserror = OSErrorEnum.EIO;
                    message = getMessage(e);
                }
            }
        } else if (e instanceof SecurityException) {
            oserror = OSErrorEnum.EPERM;
        } else if (e instanceof IllegalArgumentException) {
            oserror = OSErrorEnum.EINVAL;
        } else if (e instanceof UnsupportedOperationException) {
            oserror = OSErrorEnum.EOPNOTSUPP;
        } else if (e instanceof NonReadableChannelException || e instanceof NonWritableChannelException) {
            oserror = OSErrorEnum.EBADF;
        } else if (e instanceof RuntimeException) {
            throw (RuntimeException) e;
        } else {
            throw new RuntimeException(getMessage(e), e);
        }
        if (message == null) {
            message = oserror.getMessage();
        }
        return execute(frame, new Object[]{oserror.getNumber(), message, (filename != null) ? filename : PNone.NONE, PNone.NONE, (filename2 != null) ? filename2 : PNone.NONE});
    }

    @Specialization
    PException raiseOSError(VirtualFrame frame, Object[] arguments,
                    @Cached CallVarargsMethodNode callNode,
                    @CachedContext(PythonLanguage.class) PythonContext context) {
        PythonCore core = context.getCore();
        PBaseException error = (PBaseException) callNode.execute(frame, core.lookupType(PythonBuiltinClassType.OSError), arguments, PKeyword.EMPTY_KEYWORDS);
        return PRaiseNode.raise(this, error);
    }

    public static PRaiseOSErrorNode create() {
        return PRaiseOSErrorNodeGen.create();
    }
}
