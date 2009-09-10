/*******************************************************************************
 * Copyright (c) 2006, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Wind River Systems - initial API and implementation
 *******************************************************************************/

package org.eclipse.debug.ui.actions;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.HandlerEvent;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.debug.core.IRequest;
import org.eclipse.debug.internal.ui.commands.actions.DebugCommandService;
import org.eclipse.debug.internal.ui.commands.actions.ICommandParticipant;
import org.eclipse.debug.internal.ui.commands.actions.IEnabledTarget;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.contexts.DebugContextEvent;
import org.eclipse.debug.ui.contexts.IDebugContextListener;
import org.eclipse.debug.ui.contexts.IDebugContextService;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.ISources;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Abstract base class for re-targeting command framework handlers, which 
 * delegate execution to {@link org.eclipse.debug.core.commands.IDebugCommandHandler} 
 * handlers. The specific type of <code>IDebugCommandHandler</code> is 
 * determined by the abstract {@link #getCommandType()} method.    
 * 
 * <p> Note: This class is not an implementation of the <code>IDebugCommandHandler</code>
 * interface, which was somewhat unfortunately named.  <code>IDebugCommandHandler</code> 
 * is an interface that used only by the debugger plug-ins.  This class implements 
 * {@link org.eclipse.core.commands.IHandler} interface and is to be used with the 
 * platform commands framework. </p>
 * 
 * <p>
 * Clients may subclass this class.
 * </p>
 * @see org.eclipse.debug.core.commands.IDebugCommandHandler
 * @see org.eclipse.core.commands.IHandler
 *
 * @since 3.6
 */
public abstract class DebugCommandHandler extends AbstractHandler {

    /**
     * The DebugCommandService is able to evaluate the command handler 
     * enablement in each workbench window separately, however the workbench
     * command framework uses only a single handler instance for all windows.
     * This IEnabledTarget implementation tracks enablement of the command
     * for a given window.  When the handler enablement is tested, the 
     * currently active window is used to determine which enabled target 
     * to use.  
     */
    private class EnabledTarget implements IEnabledTarget, IDebugContextListener {
        boolean fEnabled = getInitialEnablement();
        IWorkbenchWindow fWindow;
        
        EnabledTarget(IWorkbenchWindow window) {
            fWindow = window;
            DebugCommandService.getService(fWindow).updateCommand(getCommandType(), this);
            getContextService(fWindow).addDebugContextListener(this);
        }
        
        public void setEnabled(boolean enabled) {
            boolean oldEnabled = fEnabled;
            fEnabled = enabled;
            if (fEnabled != oldEnabled && fCurrentEnabledTarget == this) {
                fireHandlerChanged(new HandlerEvent(DebugCommandHandler.this, true, false));
            }
        }
        
        public void debugContextChanged(DebugContextEvent event) {
            DebugCommandService.getService(fWindow).postUpdateCommand(getCommandType(), this);
        }
        
        void dispose() {
            if (isDisposed()) {
                return;
            }
            getContextService(fWindow).removeDebugContextListener(this);
            fWindow = null;
        }
        
        boolean isDisposed() {
            return fWindow == null;
        }
    }

    /**
     * Window listener is used to make sure that the handler enablement 
     * is updated when the active workbench window is changed.
     */
    private IWindowListener fWindowListener =  new IWindowListener() {
        
        public void windowOpened(IWorkbenchWindow w) {
        }
    
        public void windowDeactivated(IWorkbenchWindow w) {
        }
    
        public void windowClosed(IWorkbenchWindow w) {
            EnabledTarget enabledTarget = (EnabledTarget)fEnabledTargetsMap.get(w);
            if (enabledTarget != null) {
                enabledTarget.dispose();
            }
        }
    
        public void windowActivated(IWorkbenchWindow w) {
            fCurrentEnabledTarget = (EnabledTarget)fEnabledTargetsMap.get(w);
            fireHandlerChanged(new HandlerEvent(DebugCommandHandler.this, true, false));
        }
    };
    
    /**
     * Map of enabled targets keyed by workbench window.
     */
    private Map fEnabledTargetsMap = new WeakHashMap();

    /**
     * The current enabled target, based on the active
     * workbench window.
     */
    private EnabledTarget fCurrentEnabledTarget = null;
    
    /**
      * The constructor adds the handler as the 
     */
    public DebugCommandHandler() {
        super();
        PlatformUI.getWorkbench().addWindowListener(fWindowListener);
    }
    
    /* (non-Javadoc)
     * @see org.eclipse.core.commands.AbstractHandler#setEnabled(java.lang.Object)
     */
    public void setEnabled(Object evaluationContext) {
        // This method is called with the current evaluation context
        // just prior to the isEnabled() being called.  Check the active
        // window and update the current enabled target based on it 
        fCurrentEnabledTarget = null;
        
        if (!(evaluationContext instanceof IEvaluationContext)) {
            return;
        }
        IEvaluationContext context = (IEvaluationContext) evaluationContext;
        Object _window = context.getVariable(ISources.ACTIVE_WORKBENCH_WINDOW_NAME);
        if (_window instanceof IWorkbenchWindow) {
            IWorkbenchWindow window = (IWorkbenchWindow)_window;
            fCurrentEnabledTarget = getEnabledTarget(window);
        }
    }
    
    /* (non-Javadoc)
     * @see org.eclipse.core.commands.AbstractHandler#isEnabled()
     */
    public boolean isEnabled() {
        if (fCurrentEnabledTarget == null) {
            return false;
        }
        return fCurrentEnabledTarget.fEnabled;
    }
    
    private EnabledTarget getEnabledTarget(IWorkbenchWindow window) {
        EnabledTarget target = (EnabledTarget)fEnabledTargetsMap.get(window);
        if (target == null) {
            target = new EnabledTarget(window);
            fEnabledTargetsMap.put(window, target);
        }
        return target;
    }
    
    /* (non-Javadoc)
     * @see org.eclipse.core.commands.AbstractHandler#execute(org.eclipse.core.commands.ExecutionEvent)
     */
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
        if (window == null) {
            throw new ExecutionException("No active workbench window."); //$NON-NLS-1$
        }
        fCurrentEnabledTarget = getEnabledTarget(window);

        ISelection selection = getContextService(window).getActiveContext();
        if (selection instanceof IStructuredSelection && isEnabled()) {
            IStructuredSelection ss = (IStructuredSelection) selection;
            boolean enabledAfterExecute = execute(window, ss.toArray());
            
            // enable/disable the action according to the command
            fCurrentEnabledTarget.setEnabled(enabledAfterExecute);
        }

        return null;
    }
    
    private IDebugContextService getContextService(IWorkbenchWindow window) {
        return DebugUITools.getDebugContextManager().getContextService(window);
    }
    
    /**
     * Executes this action on the given target object
     * 
     * @param target the target to perform the action on
     */
    private boolean execute(IWorkbenchWindow window, final Object[] targets) {
        DebugCommandService service = DebugCommandService.getService(window); 
    	return service.executeCommand(
    	    getCommandType(), targets, 
            new ICommandParticipant() {
                public void requestDone(org.eclipse.debug.core.IRequest request) {
                    DebugCommandHandler.this.postExecute(request, targets);
                }             
            });
    }

    /**
     * This method is called after the completion of the execution of this 
     * command.  Extending classes may override this method to perform additional
     * operation after command execution. 
     * 
     * @param request The completed request object which was given the the 
     * debug command handler.
     * @param targets Objects which were the targets of this action
     */
    protected void postExecute(IRequest request, Object[] targets) {
        // do nothing by default
    }

    /**
     * Returns the {@link org.eclipse.debug.core.commands.IDebugCommandHandler} 
     * command handler that type this action executes.
     * 
     * @return command class.
     * 
     * @see org.eclipse.debug.core.commands.IDebugCommandHandler
     */
    abstract protected Class getCommandType();  

    
    /**
     * Returns whether this action should be enabled when initialized
     * and there is no active debug context.
     * 
     * @return false, by default
     */
    protected boolean getInitialEnablement() {
    	return false;
    }


    /**
     * Clean up when removing
     */
    public void dispose() {
        PlatformUI.getWorkbench().removeWindowListener(fWindowListener);        
        for (Iterator itr = fEnabledTargetsMap.values().iterator(); itr.hasNext();) {
            EnabledTarget target = (EnabledTarget)itr.next();
            if (!target.isDisposed()) {
                target.dispose();
            }
        }
        fEnabledTargetsMap.clear();
        fCurrentEnabledTarget = null;
    }
}
