/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.vcs.log.data;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Consumer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentEnumerator;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogHashMap;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.util.PersistentUtil;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Supports the int <-> Hash persistent mapping.
 */
public class VcsLogHashMapImpl implements Disposable, VcsLogHashMap {

  public static final VcsLogHashMap EMPTY = new VcsLogHashMap() {
    @Override
    public int getCommitIndex(@NotNull Hash hash, @NotNull VirtualFile root) {
      return 0;
    }

    @NotNull
    @Override
    public CommitId getCommitId(int commitIndex) {
      throw new UnsupportedOperationException("Illegal access to empty hash map by index " + commitIndex);
    }

    @Nullable
    @Override
    public CommitId findCommitId(@NotNull Condition<CommitId> string) {
      return null;
    }

    @Override
    public void flush() {
    }
  };

  @NotNull private static final Logger LOG = Logger.getInstance(VcsLogHashMap.class);
  @NotNull private static final String STORAGE_KIND = "hashes";
  private static final int VERSION = 3;
  @NotNull private static final String ROOT_STORAGE_KIND = "roots";
  private static final int ROOTS_STORAGE_VERSION = 0;

  private static final int NO_INDEX = -1;

  @NotNull private final PersistentEnumerator<CommitId> myPersistentEnumerator;
  @NotNull private final Consumer<Exception> myExceptionReporter;

  public VcsLogHashMapImpl(@NotNull Project project,
                           @NotNull Map<VirtualFile, VcsLogProvider> logProviders,
                           @NotNull Consumer<Exception> exceptionReporter,
                           @NotNull Disposable parent) throws IOException {
    myExceptionReporter = exceptionReporter;

    List<VirtualFile> roots =
      logProviders.keySet().stream().sorted((o1, o2) -> o1.getPath().compareTo(o2.getPath())).collect(Collectors.toList());

    myPersistentEnumerator = PersistentUtil.createPersistentEnumerator(new MyCommitIdKeyDescriptor(roots), STORAGE_KIND,
                                                                       PersistentUtil.calcLogId(project, logProviders), VERSION);

    // cleanup old root storages, to remove after 2016.3 release
    PersistentUtil
      .cleanupOldStorageFile(project.getName() + "." + project.getBaseDir().getPath().hashCode(), ROOT_STORAGE_KIND, ROOTS_STORAGE_VERSION);

    Disposer.register(parent, this);
  }


  @Nullable
  private CommitId doGetCommitId(int index) throws IOException {
    return myPersistentEnumerator.valueOf(index);
  }

  private int getOrPut(@NotNull Hash hash, @NotNull VirtualFile root) throws IOException {
    return myPersistentEnumerator.enumerate(new CommitId(hash, root));
  }

  @Override
  public int getCommitIndex(@NotNull Hash hash, @NotNull VirtualFile root) {
    try {
      return getOrPut(hash, root);
    }
    catch (IOException e) {
      myExceptionReporter.consume(e);
    }
    return NO_INDEX;
  }

  @Override
  @Nullable
  public CommitId getCommitId(int commitIndex) {
    try {
      CommitId commitId = doGetCommitId(commitIndex);
      if (commitId == null) {
        myExceptionReporter.consume(new RuntimeException("Unknown commit index: " + commitIndex));
      }
      return commitId;
    }
    catch (IOException e) {
      myExceptionReporter.consume(e);
    }
    return null;
  }

  @Override
  @Nullable
  public CommitId findCommitId(@NotNull final Condition<CommitId> condition) {
    try {
      final Ref<CommitId> hashRef = Ref.create();
      myPersistentEnumerator.iterateData(new CommonProcessors.FindProcessor<CommitId>() {
        @Override
        protected boolean accept(CommitId commitId) {
          boolean matches = condition.value(commitId);
          if (matches) {
            hashRef.set(commitId);
          }
          return matches;
        }
      });
      return hashRef.get();
    }
    catch (IOException e) {
      myExceptionReporter.consume(e);
      return null;
    }
  }

  public void flush() {
    myPersistentEnumerator.force();
  }

  @Override
  public void dispose() {
    try {
      myPersistentEnumerator.close();
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  private static class MyCommitIdKeyDescriptor implements KeyDescriptor<CommitId> {
    @NotNull private final List<VirtualFile> myRoots;
    @NotNull private final TObjectIntHashMap<VirtualFile> myRootsReversed;

    public MyCommitIdKeyDescriptor(@NotNull List<VirtualFile> roots) {
      myRoots = roots;

      myRootsReversed = new TObjectIntHashMap<>();
      for (int i = 0; i < roots.size(); i++) {
        myRootsReversed.put(roots.get(i), i);
      }
    }

    @Override
    public void save(@NotNull DataOutput out, CommitId value) throws IOException {
      ((HashImpl)value.getHash()).write(out);
      out.writeInt(myRootsReversed.get(value.getRoot()));
    }

    @Override
    public CommitId read(@NotNull DataInput in) throws IOException {
      Hash hash = HashImpl.read(in);
      VirtualFile root = myRoots.get(in.readInt());
      if (root == null) return null;
      return new CommitId(hash, root);
    }

    @Override
    public int getHashCode(CommitId value) {
      return value.hashCode();
    }

    @Override
    public boolean isEqual(CommitId val1, CommitId val2) {
      return val1.equals(val2);
    }
  }
}
