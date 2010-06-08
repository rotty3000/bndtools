package bndtools.wizards.workspace;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.ui.IImportWizard;
import org.eclipse.ui.IWorkbench;

import aQute.bnd.service.RepositoryPlugin;
import bndtools.Plugin;
import bndtools.tasks.repo.LocalRepositoryTasks;

public class InitialiseCnfProjectWizard extends Wizard implements IImportWizard {

    private final InitialiseCnfProjectIntroWizardPage introPage = new InitialiseCnfProjectIntroWizardPage("introPage"); //$NON-NLS-1$

	private IWorkbench workbench;
	private IStructuredSelection selection;

	public InitialiseCnfProjectWizard() {
	    setNeedsProgressMonitor(true);
	}

    /**
     * Show the wizard if it needs to be shown (i.e. the cnf project does not
     * exist and the preference to show the wizard has not been disabled). This
     * method must be called in the UI thread.
     *
     * @return Whether the wizard was shown and finished successfully.
     */
	public boolean showIfNeeded(boolean ignorePreference) {
	    boolean shownOkay = false;
	    if(!LocalRepositoryTasks.isBndWorkspaceConfigured()) {
            IPreferenceStore store = Plugin.getDefault().getPreferenceStore();
            boolean hideWizard = store.getBoolean(Plugin.PREF_HIDE_INITIALISE_CNF_WIZARD);

            if(!hideWizard || ignorePreference) {
                WizardDialog dialog = new WizardDialog(getShell(), this);
                shownOkay = dialog.open() == Window.OK;
            }
	    }
	    return shownOkay;
	}

	public void init(IWorkbench workbench, IStructuredSelection selection) {
		this.workbench = workbench;
		this.selection = selection;
	}
	@Override
	public void addPages() {
	    addPage(introPage);
	}
	@Override
	public boolean performFinish() {
	    try {
	        final MultiStatus status = new MultiStatus(Plugin.PLUGIN_ID, 0, "Problems occurred while configuring the Bnd workspace.", null);
	        final IWorkspaceRunnable workspaceOp = new IWorkspaceRunnable() {
                public void run(IProgressMonitor monitor) throws CoreException {
                    SubMonitor progress = SubMonitor.convert(monitor, "Copying files to repository...", 4);

                    LocalRepositoryTasks.configureBndWorkspace(progress.newChild(1));
                    RepositoryPlugin repo = LocalRepositoryTasks.getLocalRepository();
                    LocalRepositoryTasks.installImplicitRepositoryContents(repo, status, progress.newChild(2));
                    LocalRepositoryTasks.refreshWorkspaceForRepository(progress.newChild(1));
                }
            };
            getContainer().run(false, false, new IRunnableWithProgress() {
                public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
                    try {
                        ResourcesPlugin.getWorkspace().run(workspaceOp, monitor);
                    } catch (CoreException e) {
                        throw new InvocationTargetException(e);
                    }
                }
            });

            if(!status.isOK())
                ErrorDialog.openError(getShell(), "Warning", null, status);

            return true;
        } catch (InvocationTargetException e) {
            ErrorDialog.openError(getShell(), "Error", null, new Status(IStatus.ERROR, Plugin.PLUGIN_ID, 0, "Error creating workspace configuration project.", e.getCause()));
        } catch (InterruptedException e) {
            // Can't happen?
        }
        return false;
	}
	@Override
	public boolean performCancel() {
	    IPreferenceStore store = Plugin.getDefault().getPreferenceStore();
	    boolean hide = store.getBoolean(Plugin.PREF_HIDE_INITIALISE_CNF_ADVICE);

	    if(!hide) {
	        MessageDialogWithToggle dialog = MessageDialogWithToggle.openInformation(getShell(), Messages.InitialiseCnfProjectWizard_info_dialog_popup, Messages.InitialiseCnfProjectWizard_info_dialog_message, Messages.InitialiseCnfProjectWizard_info_dialog_donotshow, false, null, null);
	        if(dialog.getToggleState()) {
	            store.setValue(Plugin.PREF_HIDE_INITIALISE_CNF_ADVICE, true);
	        }
	    }
	    return super.performCancel();
	}
}