/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.actions.expressions;

 
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IExpressionManager;
import org.eclipse.debug.core.IExpressionsListener;
import org.eclipse.debug.core.model.IExpression;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.actions.AbstractRemoveAllActionDelegate;
import org.eclipse.debug.internal.ui.actions.ActionMessages;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbenchWindow;

/**
 * Removes all expressions from the expressions view.
 */
public class RemoveAllExpressionsAction extends AbstractRemoveAllActionDelegate implements IExpressionsListener {

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IActionDelegate#run(org.eclipse.jface.action.IAction)
	 */
	public void run(IAction action) {
		IWorkbenchWindow window = DebugUIPlugin.getActiveWorkbenchWindow();
		if (window != null) {
			boolean proceed = MessageDialog.openQuestion(window.getShell(), ActionMessages.RemoveAllExpressionsAction_0, ActionMessages.RemoveAllExpressionsAction_1);  
			if (proceed) {
				IExpressionManager manager = DebugPlugin.getDefault().getExpressionManager();
				IExpression[] expressions= manager.getExpressions();
				manager.removeExpressions(expressions);
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.actions.selection.AbstractRemoveAllActionDelegate#isEnabled()
	 */
	protected boolean isEnabled() {
		return DebugPlugin.getDefault().getExpressionManager().hasExpressions();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.actions.selection.AbstractRemoveAllActionDelegate#initialize()
	 */
	protected void initialize() {
		DebugPlugin.getDefault().getExpressionManager().addExpressionListener(this);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.actions.selection.AbstractRemoveAllActionDelegate#dispose()
	 */
	public void dispose() {
		DebugPlugin.getDefault().getExpressionManager().removeExpressionListener(this);
		super.dispose();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.IExpressionsListener#expressionsAdded(org.eclipse.debug.core.model.IExpression[])
	 */
	public void expressionsAdded(IExpression[] expressions) {
		update();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.IExpressionsListener#expressionsRemoved(org.eclipse.debug.core.model.IExpression[])
	 */
	public void expressionsRemoved(IExpression[] expressions) {
		update();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.IExpressionsListener#expressionsChanged(org.eclipse.debug.core.model.IExpression[])
	 */
	public void expressionsChanged(IExpression[] expressions) {}
}
