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
package com.oracle.graal.python.builtins.objects.cext;

import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_GET_OB_TYPE;
import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_PY_OBJECT_GENERIC_GET_DICT;

import java.util.Objects;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetTypeMemberNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ImportCAPISymbolNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.llvm.spi.ReferenceLibrary;

@ExportLibrary(PythonObjectLibrary.class)
@ExportLibrary(ReferenceLibrary.class)
@ExportLibrary(InteropLibrary.class)
public final class PythonAbstractNativeObject extends PythonAbstractObject implements PythonNativeObject, PythonNativeClass {

    public final TruffleObject object;

    public PythonAbstractNativeObject(TruffleObject object) {
        this.object = object;
    }

    public int compareTo(Object o) {
        return 0;
    }

    @SuppressWarnings("static-method")
    public Shape getInstanceShape() {
        CompilerDirectives.transferToInterpreter();
        throw new UnsupportedOperationException("native class does not have a shape");
    }

    public void lookupChanged() {
        // TODO invalidate cached native MRO
        CompilerDirectives.transferToInterpreter();
        throw new UnsupportedOperationException("not yet implemented");
    }

    public TruffleObject getPtr() {
        return object;
    }

    @Override
    public int hashCode() {
        CompilerAsserts.neverPartOfCompilation();
        // this is important for the default '__hash__' implementation
        return Objects.hashCode(object);
    }

    @Ignore
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        PythonAbstractNativeObject other = (PythonAbstractNativeObject) obj;
        return Objects.equals(object, other.object);
    }

    public boolean equalsProfiled(Object obj, ValueProfile profile) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        PythonAbstractNativeObject other = (PythonAbstractNativeObject) obj;
        return Objects.equals(profile.profile(object), profile.profile(other.object));
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return String.format("PythonAbstractNativeObject(%s)", object);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean hasDict() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings({"static-method", "unused"})
    public void setDict(PHashingCollection value) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    public Object asIndexWithState(ThreadState threadState,
                    @Exclusive @Cached GetLazyClassNode getClass,
                    @Exclusive @Cached IsSubtypeNode isSubtypeNode,
                    // arguments for super-implementation call
                    @CachedLibrary(limit = "1") PythonObjectLibrary lib,
                    @Exclusive @Cached PRaiseNode raise,
                    @Exclusive @Cached CallUnaryMethodNode callNode,
                    @Exclusive @Cached IsSubtypeNode isSubtype,
                    @Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookupIndex,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile noIndex,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile resultProfile,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
        if (isSubtypeNode.execute(getClass.execute(this), PythonBuiltinClassType.PInt)) {
            return this; // subclasses of 'int' should do early return
        } else {
            return asIndexWithState(threadState, lib, raise, callNode, isSubtype, lookupIndex, noIndex, resultProfile, gotState);
        }
    }

    @ExportMessage
    @GenerateUncached
    public abstract static class GetDict {
        @Specialization
        public static PHashingCollection getNativeDictionary(PythonAbstractNativeObject self,
                        @Exclusive @Cached PRaiseNode raiseNode,
                        @Exclusive @Cached ToSulongNode toSulong,
                        @Exclusive @Cached ToJavaNode toJava,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                        @Exclusive @Cached ImportCAPISymbolNode importCAPISymbolNode) {
            try {
                Object func = importCAPISymbolNode.execute(FUN_PY_OBJECT_GENERIC_GET_DICT);
                Object javaDict = toJava.execute(interopLibrary.execute(func, toSulong.execute(self)));
                if (javaDict instanceof PHashingCollection) {
                    return (PHashingCollection) javaDict;
                } else if (javaDict == PNone.NO_VALUE) {
                    return null;
                } else {
                    throw raiseNode.raise(PythonBuiltinClassType.TypeError, "__dict__ must have been set to a dictionary, not a '%p'", javaDict);
                }
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("could not run our core function to get the dict of a native object", e);
            }
        }
    }

    @ExportMessage
    @GenerateUncached
    @SuppressWarnings("unused")
    public abstract static class GetLazyPythonClass {
        public static Assumption getSingleContextAssumption() {
            return PythonLanguage.getCurrent().singleContextAssumption;
        }

        @Specialization(guards = "object == cachedObject", limit = "1", assumptions = "singleContextAssumption")
        public static PythonAbstractClass getNativeClassCachedIdentity(PythonAbstractNativeObject object,
                        @Shared("assumption") @Cached(value = "getSingleContextAssumption()") Assumption singleContextAssumption,
                        @Exclusive @Cached("object") PythonAbstractNativeObject cachedObject,
                        @Exclusive @Cached("getNativeClassUncached(cachedObject)") PythonAbstractClass cachedClass) {
            // TODO: (tfel) is this really something we can do? It's so rare for this class to
            // change that it shouldn't be worth the effort, but in native code, anything can
            // happen. OTOH, CPython also has caches that can become invalid when someone just
            // goes and changes the ob_type of an object.
            return cachedClass;
        }

        @Specialization(guards = "referenceLibrary.isSame(cachedObject.object, object.object)", limit = "1", assumptions = "singleContextAssumption")
        public static PythonAbstractClass getNativeClassCached(PythonAbstractNativeObject object,
                        @Shared("assumption") @Cached(value = "getSingleContextAssumption()") Assumption singleContextAssumption,
                        @Exclusive @Cached("object") PythonAbstractNativeObject cachedObject,
                        @Exclusive @Cached("getNativeClassUncached(cachedObject)") PythonAbstractClass cachedClass,
                        @CachedLibrary("object.object") @SuppressWarnings("unused") ReferenceLibrary referenceLibrary) {
            // TODO same as for 'getNativeClassCachedIdentity'
            return cachedClass;
        }

        @Specialization(replaces = {"getNativeClassCached", "getNativeClassCachedIdentity"})
        public static PythonAbstractClass getNativeClass(PythonAbstractNativeObject object,
                        @Exclusive @Cached PCallCapiFunction callGetObTypeNode,
                        @Exclusive @Cached AsPythonObjectNode toJavaNode) {
            // do not convert wrap 'object.object' since that is really the native pointer
            // object
            return (PythonAbstractClass) toJavaNode.execute(callGetObTypeNode.call(FUN_GET_OB_TYPE, object.getPtr()));
        }

        public static PythonAbstractClass getNativeClassUncached(PythonAbstractNativeObject object) {
            // do not wrap 'object.object' since that is really the native pointer object
            return getNativeClass(object, PCallCapiFunction.getUncached(), AsPythonObjectNode.getUncached());
        }
    }

    @ExportMessage
    static class IsSame {

        @Specialization
        static boolean doNativeObject(PythonAbstractNativeObject receiver, PythonAbstractNativeObject other,
                        @CachedLibrary("receiver.object") ReferenceLibrary referenceLibrary) {
            return referenceLibrary.isSame(receiver.object, other.object);
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean doOther(PythonAbstractNativeObject receiver, Object other) {
            return false;
        }
    }

    @ExportMessage
    boolean isMetaObject(
                    @Shared("isType") @Cached TypeNodes.IsTypeNode isType) {
        return isType.execute(this);
    }

    @ExportMessage
    boolean isMetaInstance(Object instance,
                    @Shared("isType") @Cached TypeNodes.IsTypeNode isType,
                    @Cached GetLazyClassNode getClass,
                    @Cached IsSubtypeNode isSubtype) throws UnsupportedMessageException {
        if (!isType.execute(this)) {
            throw UnsupportedMessageException.create();
        }
        return isSubtype.execute(getClass.execute(instance), this);
    }

    @ExportMessage
    String getMetaSimpleName(
                    @Shared("isType") @Cached TypeNodes.IsTypeNode isType,
                    @Shared("getTypeMember") @Cached GetTypeMemberNode getTpNameNode) throws UnsupportedMessageException {
        return getSimpleName(getMetaQualifiedName(isType, getTpNameNode));
    }

    @TruffleBoundary
    private static String getSimpleName(String fqname) {
        int firstDot = fqname.indexOf('.');
        if (firstDot != -1) {
            return fqname.substring(firstDot + 1);
        }
        return fqname;
    }

    @ExportMessage
    String getMetaQualifiedName(
                    @Shared("isType") @Cached TypeNodes.IsTypeNode isType,
                    @Shared("getTypeMember") @Cached GetTypeMemberNode getTpNameNode) throws UnsupportedMessageException {
        if (!isType.execute(this)) {
            throw UnsupportedMessageException.create();
        }
        // 'tp_name' contains the fully-qualified name, i.e., 'module.A.B...'
        return (String) getTpNameNode.execute(this, NativeMemberNames.TP_NAME);
    }
}
