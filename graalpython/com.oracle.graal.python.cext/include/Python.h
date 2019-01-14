/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
#ifndef Py_PYTHON_H
#define Py_PYTHON_H

#define HAVE_UTIME_H
#define HAVE_UNISTD_H
#define HAVE_SIGNAL_H
#define HAVE_FCNTL_H
#define HAVE_SYS_WAIT_H

#define PYPY_VERSION 0
#define PYPY_VERSION_NUM 0

#include <truffle.h>
#include <polyglot.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/dir.h>
#include <dirent.h>
#include <locale.h>
#include <langinfo.h>
#include <assert.h>
#include <unistd.h>
#include <math.h>

#include "pyport.h"
#include "pymacro.h"
#include "object.h"
#include "abstract.h"
#include "methodobject.h"
#include "moduleobject.h"
#include "unicodeobject.h"
#include "pystate.h"
#include "pyarena.h"
#include "pythonrun.h"
#include "compile.h"
#include "ceval.h"
#include "pyerrors.h"
#include "modsupport.h"
#include "tupleobject.h"
#include "structseq.h"
#include "structmember.h"
#include "pytime.h"
#include "pymem.h"
#include "objimpl.h"
#include "bytesobject.h"
#include "longobject.h"
#include "longintrepr.h"
#include "boolobject.h"
#include "floatobject.h"
#include "dictobject.h"
#include "setobject.h"
#include "complexobject.h"
#include "listobject.h"
#include "sliceobject.h"
#include "descrobject.h"
#include "fileobject.h"
#include "pyctype.h"
#include "bytearrayobject.h"
#include "warnings.h"
#include "patchlevel.h"
#include "pymath.h"
#include "pyhash.h"
#include "import.h"
#include "pycapsule.h"
#include "pylifecycle.h"
#include "pydebug.h"
#include "code.h"
#include "pyfpe.h"
#include "memoryobject.h"
#include "pystrhex.h"
#include "codecs.h"
#include "frameobject.h"
#include "traceback.h"
#include "classobject.h"
#include "pythread.h"
#include "funcobject.h"
#include "iterobject.h"
#include "datetime.h"

// TODO: we must extend the refcounting behavior to support handles to managed objects
#undef Py_DECREF
#define Py_DECREF(o) 0
#undef Py_INCREF
#define Py_INCREF(o) 0

/* 
 * #define Py_INCREF(op) (                         \
 *     _Py_INC_REFTOTAL  _Py_REF_DEBUG_COMMA       \
 *     ((PyObject *)(op))->ob_refcnt++)
 * 
 * #define Py_DECREF(op)                                                   \
 *     do {                                                                \
 *         void* handle = op;                                              \
 *         PyObject *_py_decref_tmp = (PyObject *)((truffle_is_handle_to_managed(handle) ? truffle_managed_from_handle(handle) : handle)); \
 *         if (_Py_DEC_REFTOTAL  _Py_REF_DEBUG_COMMA                       \
 *             --(_py_decref_tmp)->ob_refcnt != 0) {                       \
 *             _Py_CHECK_REFCNT(_py_decref_tmp)                            \
 *             else                                                        \
 *                 _Py_Dealloc(_py_decref_tmp);                            \
 *     } while (0)
 */

extern PyObject* PyTruffle_Err_Format(PyObject* exception, const char* fmt, int s, void* v0, void* v1, void* v2, void* v3, void* v4, void* v5, void* v6, void* v7, void* v8, void* v9);
#define PyTruffle_Err_Format_0(EXC, FORMAT) PyTruffle_Err_Format(EXC, FORMAT, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Err_Format_1(EXC, FORMAT, V1) PyTruffle_Err_Format(EXC, FORMAT, 1, (void*)(V1), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Err_Format_2(EXC, FORMAT, V1, V2) PyTruffle_Err_Format(EXC, FORMAT, 2, (void*)(V1), (void*)(V2), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Err_Format_3(EXC, FORMAT, V1, V2, V3) PyTruffle_Err_Format(EXC, FORMAT, 3, (void*)(V1), (void*)(V2), (void*)(V3), NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Err_Format_4(EXC, FORMAT, V1, V2, V3, V4) PyTruffle_Err_Format(EXC, FORMAT, 4, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), NULL, NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Err_Format_5(EXC, FORMAT, V1, V2, V3, V4, V5) PyTruffle_Err_Format(EXC, FORMAT, 5, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), NULL, NULL, NULL, NULL, NULL)
#define PyTruffle_Err_Format_6(EXC, FORMAT, V1, V2, V3, V4, V5, V6) PyTruffle_Err_Format(EXC, FORMAT, 6, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), NULL, NULL, NULL, NULL)
#define PyTruffle_Err_Format_7(EXC, FORMAT, V1, V2, V3, V4, V5, V6, V7) PyTruffle_Err_Format(EXC, FORMAT, 7, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), NULL, NULL, NULL)
#define PyTruffle_Err_Format_8(EXC, FORMAT, V1, V2, V3, V4, V5, V6, V7, V8) PyTruffle_Err_Format(EXC, FORMAT, 8, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), NULL, NULL)
#define PyTruffle_Err_Format_9(EXC, FORMAT, V1, V2, V3, V4, V5, V6, V7, V8, V9) PyTruffle_Err_Format(EXC, FORMAT, 9, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), NULL)
#define PyTruffle_Err_Format_10(EXC, FORMAT, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10) PyTruffle_Err_Format(EXC, FORMAT, 10, (void*)(V1), (void*)(V2), (void*)(V3), (void*)(V4), (void*)(V5), (void*)(V6), (void*)(V7), (void*)(V8), (void*)(V9), (void*)V10)
#define ARG_PARSE_ERR_FORMAT_IMPL(_0, _1, _2, _3, _4, _5, _6, _7, _8, _9, _10, NAME, ...) NAME
#ifdef PyErr_Format
#undef PyErr_Format
#endif
#define PyErr_Format(EXC, ...) ARG_PARSE_ERR_FORMAT_IMPL(__VA_ARGS__, PyTruffle_Err_Format_10, PyTruffle_Err_Format_9, PyTruffle_Err_Format_8, PyTruffle_Err_Format_7, PyTruffle_Err_Format_6, PyTruffle_Err_Format_5, PyTruffle_Err_Format_4, PyTruffle_Err_Format_3, PyTruffle_Err_Format_2, PyTruffle_Err_Format_1, PyTruffle_Err_Format_0)(EXC, __VA_ARGS__)

#endif
