/*
 * Copyright 2017-2018 Jan Buchinger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.janbuchinger.code.fssync.sync;

import java.io.File;
import java.util.Iterator;
import java.util.Vector;

import javax.swing.SwingWorker;

public final class OperationSummary {
	// updateSizeSourceModified - updateSizeSourceModifiedOld
	private long deltaModSource;
	private long deltaModDestination;

	// updateSizeSource - updateSizeSourceModifiedOld - rmSizeSource
	private long sumSource;
	private long sumDestination;

	// updateSizeSource + updateSizeDestination
	private long updateSizeTotal;

	// updateSizeSourceNew + updateSizeSourceModified
	private long updateSizeSource;
	private long updateSizeDestination;

	private long freeSpaceSource;
	private long freeSpaceDestination;

	// param data
	private long updateSizeSourceNew;
	private long updateSizeDestinationNew;
	private long updateSizeSourceModified;
	private long updateSizeDestinationModified;
	private long updateSizeSourceModifiedOld;
	private long updateSizeDestinationModifiedOld;
	private long rmSizeSource;
	private long rmSizeDestination;

	private final Vector<File> corruptFilesSource;
	private final Vector<File> corruptFilesDestination;
	private final Vector<File> lostFiles;

	private final Vector<CopyAction> copyActions;
	private final Vector<Vector<CopyAction>> copyActionsDuplicates;
	private final Vector<DeleteAction> deleteActions;

	private final SwingWorker<Void, Void> sp;

	private int nCopyActionsSelected;
	private int nDeleteActionsSelected;

	private final boolean isRestore;

	public OperationSummary(File source, File destination, Vector<File> corruptFilesSource,
			Vector<File> corruptFilesDestination, Vector<File> lostFiles, Vector<CopyAction> copyActions,
			Vector<DeleteAction> deleteActions, SwingWorker<Void, Void> sp, boolean isRestore,
			boolean isBiDirectional) throws SynchronizationCancelledException {
		this.sp = sp;

		this.corruptFilesSource = corruptFilesSource;
		this.corruptFilesDestination = corruptFilesDestination;
		this.lostFiles = lostFiles;

		this.copyActions = copyActions;
		this.copyActionsDuplicates = new Vector<Vector<CopyAction>>();
		this.deleteActions = deleteActions;

		this.isRestore = isRestore;

		nCopyActionsSelected = 0;
		nDeleteActionsSelected = 0;

		updateSizeSourceNew = 0;
		updateSizeDestinationNew = 0;

		updateSizeSourceModified = 0;
		updateSizeDestinationModified = 0;

		updateSizeSourceModifiedOld = 0;
		updateSizeDestinationModifiedOld = 0;

		rmSizeSource = 0;
		rmSizeDestination = 0;

		// if bidirectional then check for conflicts
		if (isBiDirectional) {
			int i1 = 0;
			int i2;
			String relativePath;
			Iterator<CopyAction> iCopyActions;
			CopyAction copyAction1;
			CopyAction copyAction2;
			Vector<CopyAction> duplicate;

			while (i1 < copyActions.size()) {
				copyAction1 = copyActions.get(i1);
				if (copyAction1.getConflict() != null) {
					i1++;
					continue;
				}
				relativePath = copyAction1.getRelativePath();
				iCopyActions = copyActions.iterator();
				i2 = 0;
				while (iCopyActions.hasNext()) {
					copyAction2 = iCopyActions.next();
					if (i2 <= i1) {
						i2++;
					} else if (copyAction2.getConflict() != null) {
						// do nothing
					} else if (copyAction2.getRelativePath().equals(relativePath)) {
						copyAction2.setConflict(copyAction1);
						copyAction1.setConflict(copyAction2);
						duplicate = new Vector<>();
						duplicate.add(copyAction1);
						duplicate.add(copyAction2);
						copyActionsDuplicates.add(duplicate);
						if (copyAction2.getDirection() == CopyAction.DIR_BACKUP) {
							copyAction1.setSelected(false);
						} else {
							copyAction2.setSelected(false);
						}
						break;
					}
					if (sp.isCancelled()) {
						throw new SynchronizationCancelledException();
					} 
				}
				i1++;
			}
		}

		reCalcAll();

		freeSpaceDestination = destination.getFreeSpace();
		freeSpaceSource = source.getFreeSpace();
	}

	public void reCalcAll() throws SynchronizationCancelledException {

		Iterator<CopyAction> iCopyActions = copyActions.iterator();
		CopyAction copyAction;

		nCopyActionsSelected = 0;
		nDeleteActionsSelected = 0;

		updateSizeSourceNew = 0;
		updateSizeSourceModified = 0;
		updateSizeSourceModifiedOld = 0;
		rmSizeSource = 0;

		updateSizeDestinationNew = 0;
		updateSizeDestinationModified = 0;
		updateSizeDestinationModifiedOld = 0;
		rmSizeDestination = 0;

		while (iCopyActions.hasNext()) {
			copyAction = iCopyActions.next();
			if (!copyAction.isSelected())
				continue;
			nCopyActionsSelected++;
			if (copyAction.getDirection() == CopyAction.DIR_RESTORE) {
				if (copyAction.isNew()) {
					updateSizeSourceNew += copyAction.getSource().length();
				} else {
					updateSizeSourceModified += copyAction.getSource().length();
					updateSizeSourceModifiedOld += copyAction.getDestination().length();
				}
			} else {
				if (copyAction.isNew()) {
					updateSizeDestinationNew += copyAction.getSource().length();
				} else {
					updateSizeDestinationModified += copyAction.getSource().length();
					updateSizeDestinationModifiedOld += copyAction.getDestination().length();
				}
			}
			if (sp.isCancelled()) {
				throw new SynchronizationCancelledException();
			}
		}

		Iterator<DeleteAction> iDeleteActions = deleteActions.iterator();
		DeleteAction deleteAction;
		while (iDeleteActions.hasNext()) {
			deleteAction = iDeleteActions.next();
			if (deleteAction.isSelected()) {
				nDeleteActionsSelected++;
				if (deleteAction.getLocation() == DeleteAction.del_source) {
					rmSizeSource += deleteAction.getFile().length();
				} else {
					rmSizeDestination += deleteAction.getFile().length();
				}
			} else {
				nCopyActionsSelected++;
				if (deleteAction.getLocation() == DeleteAction.del_source) {
					updateSizeDestinationNew += deleteAction.getFile().length();
				} else {
					updateSizeSourceNew += deleteAction.getFile().length();
				}
			}
			if (sp.isCancelled()) {
				throw new SynchronizationCancelledException();
			}
		}

		reCalc();
	}

	public void reCalc() {
		// updateSizeSourceModified - updateSizeSourceModifiedOld
		deltaModSource = updateSizeSourceModified - updateSizeSourceModifiedOld;
		deltaModDestination = updateSizeDestinationModified - updateSizeDestinationModifiedOld;

		// updateSizeSourceNew + updateSizeSourceModified
		updateSizeSource = updateSizeSourceNew + updateSizeSourceModified;
		updateSizeDestination = updateSizeDestinationNew + updateSizeDestinationModified;

		// updateSizeSource + updateSizeDestination
		updateSizeTotal = updateSizeSource + updateSizeDestination;

		// updateSizeSource - updateSizeSourceModifiedOld - rmSizeSource
		sumSource = updateSizeSource - updateSizeSourceModifiedOld - rmSizeSource;
		sumDestination = updateSizeDestination - updateSizeDestinationModifiedOld - rmSizeDestination;
	}

	public final Vector<File> getCorruptFilesSource() {
		return corruptFilesSource;
	}

	public final Vector<File> getCorruptFilesDestination() {
		return corruptFilesDestination;
	}

	public final Vector<File> getLostFiles() {
		return lostFiles;
	}

	public final Vector<CopyAction> getCopyActions() {
		return copyActions;
	}

	public final Vector<Vector<CopyAction>> getCopyActionsDuplicates() {
		return copyActionsDuplicates;
	}

	public final Vector<DeleteAction> getDeleteActions() {
		return deleteActions;
	}

	public final long getFreeSpaceSource() {
		return freeSpaceSource;
	}

	public final long getFreeSpaceDestination() {
		return freeSpaceDestination;
	}

	public final long getDeltaModSource() {
		return deltaModSource;
	}

	public final long getDeltaModDestination() {
		return deltaModDestination;
	}

	public final long getSumSource() {
		return sumSource;
	}

	public final long getSumDestination() {
		return sumDestination;
	}

	public final long getUpdateSizeTotal() {
		return updateSizeTotal;
	}

	public final long getUpdateSizeSource() {
		return updateSizeSource;
	}

	public final long getUpdateSizeDestination() {
		return updateSizeDestination;
	}

	public final long getUpdateSizeSourceNew() {
		return updateSizeSourceNew;
	}

	public final long getUpdateSizeDestinationNew() {
		return updateSizeDestinationNew;
	}

	public final long getUpdateSizeSourceModified() {
		return updateSizeSourceModified;
	}

	public final long getUpdateSizeDestinationModified() {
		return updateSizeDestinationModified;
	}

	public final long getUpdateSizeSourceModifiedOld() {
		return updateSizeSourceModifiedOld;
	}

	public final long getUpdateSizeDestinationModifiedOld() {
		return updateSizeDestinationModifiedOld;
	}

	public final long getRmSizeSource() {
		return rmSizeSource;
	}

	public final long getRmSizeDestination() {
		return rmSizeDestination;
	}

	public void addDestinationNew(long length) {
		updateSizeDestinationNew += length;
		nCopyActionsSelected++;
	}

	public void addDestinationModified(long length, long lengthOld) {
		updateSizeDestinationModified += length;
		updateSizeDestinationModifiedOld += lengthOld;
		nCopyActionsSelected++;
	}

	public void removeDestinationNew(long length) {
		updateSizeDestinationNew -= length;
		nCopyActionsSelected--;
	}

	public void removeDestinationModified(long length, long lengthOld) {
		updateSizeDestinationModified -= length;
		updateSizeDestinationModifiedOld -= lengthOld;
		nCopyActionsSelected--;
	}

	public void addSourceNew(long length) {
		updateSizeSourceNew += length;
		nCopyActionsSelected++;
	}

	public void addSourceModified(long length, long lengthOld) {
		updateSizeSourceModified += length;
		updateSizeSourceModifiedOld += lengthOld;
		nCopyActionsSelected++;
	}

	public void removeSourceNew(long length) {
		updateSizeSourceNew -= length;
		nCopyActionsSelected--;
	}

	public void removeSourceModified(long length, long lengthOld) {
		updateSizeSourceModified -= length;
		updateSizeSourceModifiedOld -= lengthOld;
		nCopyActionsSelected--;
	}

	public void addRmDestination(long length) {
		rmSizeDestination += length;
		if (!isRestore) {
			updateSizeSourceNew -= length;
			nDeleteActionsSelected++;
			nCopyActionsSelected--;
		}
	}

	public void addRmSource(long length) {
		rmSizeSource += length;
		if (!isRestore) {
			updateSizeDestinationNew -= length;
			nDeleteActionsSelected++;
			nCopyActionsSelected--;
		}
	}

	public void removeRmDestination(long length) {
		rmSizeDestination -= length;
		if (!isRestore) {
			updateSizeSourceNew += length;
			nDeleteActionsSelected--;
			nCopyActionsSelected++;
		}
	}

	public void removeRmSource(long length) {
		rmSizeSource -= length;
		if (!isRestore) {
			updateSizeDestinationNew += length;
			nDeleteActionsSelected--;
			nCopyActionsSelected++;
		}
	}

	public final int getnCopyActionsSelected() {
		return nCopyActionsSelected;
	}

	public final int getnDeleteActionsSelected() {
		return nDeleteActionsSelected;
	}

	public final boolean shouldDisplayDialog() {
		return corruptFilesSource.size() + corruptFilesDestination.size() + lostFiles.size()
				+ copyActions.size() + deleteActions.size() > 0;
	}

	public boolean hasCorruptFiles() {
		return corruptFilesSource.size() + corruptFilesDestination.size() + lostFiles.size() > 0;
	}
	
	public boolean hasConflicts() {
		return copyActionsDuplicates.size() > 0;
	}

	public final boolean isRestore() {
		return isRestore;
	}
}
