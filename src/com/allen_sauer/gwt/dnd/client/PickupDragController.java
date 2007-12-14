/*
 * Copyright 2007 Fred Sauer
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.allen_sauer.gwt.dnd.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.AbsolutePanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

import com.allen_sauer.gwt.dnd.client.drop.BoundaryDropController;
import com.allen_sauer.gwt.dnd.client.drop.DropController;
import com.allen_sauer.gwt.dnd.client.util.DOMUtil;
import com.allen_sauer.gwt.dnd.client.util.Location;
import com.allen_sauer.gwt.dnd.client.util.WidgetArea;
import com.allen_sauer.gwt.dnd.client.util.WidgetLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * DragController used for drag-and-drop operations where a draggable widget or
 * drag proxy is temporarily picked up and dragged around the boundary panel.
 * Be sure to register a {@link DropController} for each drop target.
 * 
 * @see #registerDropController(DropController)
 */
public class PickupDragController extends AbstractDragController {
  private static class SavedWidgetInfo {
    int initialDraggableIndex;
    String initialDraggableMargin;
    Widget initialDraggableParent;
    Location initialDraggableParentLocation;
  }

  /**
   * @deprecated Instead selectively use your own CSS classes.
   */
  protected static final String CSS_MOVABLE_PANEL;

  /**
   * @deprecated Instead selectively use your own CSS classes.
   */
  protected static final String CSS_PROXY;
  private static final String PRIVATE_CSS_MOVABLE_PANEL = "dragdrop-movable-panel";
  private static final String PRIVATE_CSS_PROXY = "dragdrop-proxy";

  static {
    CSS_MOVABLE_PANEL = PRIVATE_CSS_MOVABLE_PANEL;
    CSS_PROXY = PRIVATE_CSS_PROXY;
  }

  private BoundaryDropController boundaryDropController;
  private int boundaryOffsetX;
  private int boundaryOffsetY;
  private boolean dragProxyEnabled = false;
  private DropControllerCollection dropControllerCollection;
  private ArrayList dropControllerList = new ArrayList();
  private int dropTargetClientHeight;
  private int dropTargetClientWidth;
  private Widget movablePanel;
  private HashMap savedWidgetInfoMap;

  /**
   * Create a new pickup-and-move style drag controller. Allows widgets or a
   * suitable proxy to be temporarily picked up and moved around the specified
   * boundary panel.
   * 
   * <p>
   * Note: An implicit {@link BoundaryDropController} is created and registered
   * automatically.
   * </p>
   * 
   * @param boundaryPanel the desired boundary panel or <code>RootPanel.get()</code>
   *                      if entire document body is to be the boundary
   * @param allowDroppingOnBoundaryPanel whether or not boundary panel should
   *            allow dropping
   */
  public PickupDragController(AbsolutePanel boundaryPanel, boolean allowDroppingOnBoundaryPanel) {
    super(boundaryPanel);
    assert boundaryPanel != null : "Use 'RootPanel.get()' instead of 'null'.";
    boundaryDropController = newBoundaryDropController(boundaryPanel, allowDroppingOnBoundaryPanel);
    registerDropController(boundaryDropController);
    dropControllerCollection = new DropControllerCollection(dropControllerList);
  }

  public void dragEnd() {
    if (context.vetoException != null) {
      if (!getBehaviorDragProxy()) {
        restoreSelectedWidgetsLocation();
      }
    } else {
      context.dropController.onDrop(context);
    }
    context.dropController.onLeave(context);
    context.dropController = null;

    if (!getBehaviorDragProxy()) {
      restoreSelectedWidgetsStyle();
    }
    movablePanel.removeFromParent();
    movablePanel = null;
    super.dragEnd();
  }

  public void dragMove() {
    int desiredLeft = context.desiredDraggableX - boundaryOffsetX;
    int desiredTop = context.desiredDraggableY - boundaryOffsetY;
    if (getBehaviorConstrainedToBoundaryPanel()) {
      desiredLeft = Math.max(0, Math.min(desiredLeft, dropTargetClientWidth
          - context.draggable.getOffsetWidth()));
      desiredTop = Math.max(0, Math.min(desiredTop, dropTargetClientHeight
          - context.draggable.getOffsetHeight()));
    }

    DOMUtil.fastSetElementPosition(movablePanel.getElement(), desiredLeft, desiredTop);

    DropController newDropController = getIntersectDropController(context.mouseX, context.mouseY);
    if (context.dropController != newDropController) {
      if (context.dropController != null) {
        context.dropController.onLeave(context);
      }
      context.dropController = newDropController;
      if (context.dropController != null) {
        context.dropController.onEnter(context);
      }
    }

    if (context.dropController != null) {
      context.dropController.onMove(context);
    }
  }

  public void dragStart() {
    super.dragStart();

    WidgetLocation currentDraggableLocation = new WidgetLocation(context.draggable,
        context.boundaryPanel);
    if (getBehaviorDragProxy()) {
      movablePanel = newDragProxy(context);
      context.boundaryPanel.add(movablePanel, currentDraggableLocation.getLeft(),
          currentDraggableLocation.getTop());
    } else {
      saveSelectedWidgetsLocationAndStyle();
      AbsolutePanel container = new AbsolutePanel();
      DOM.setStyleAttribute(container.getElement(), "overflow", "visible");

      container.setPixelSize(context.draggable.getOffsetWidth(),
          context.draggable.getOffsetHeight());
      context.boundaryPanel.add(container, currentDraggableLocation.getLeft(),
          currentDraggableLocation.getTop());

      int draggableAbsoluteLeft = context.draggable.getAbsoluteLeft();
      int draggableAbsoluteTop = context.draggable.getAbsoluteTop();
      for (Iterator iterator = context.selectedWidgets.iterator(); iterator.hasNext();) {
        Widget widget = (Widget) iterator.next();
        if (widget != context.draggable) {
          int relativeX = widget.getAbsoluteLeft() - draggableAbsoluteLeft;
          int relativeY = widget.getAbsoluteTop() - draggableAbsoluteTop;
          container.add(widget, relativeX, relativeY);
        }
      }
      container.add(context.draggable, 0, 0);
      movablePanel = container;
    }
    movablePanel.addStyleName(PRIVATE_CSS_MOVABLE_PANEL);

    // one time calculation of boundary panel location for efficiency during dragging
    Location widgetLocation = new WidgetLocation(context.boundaryPanel, null);
    boundaryOffsetX = widgetLocation.getLeft()
        + DOMUtil.getBorderLeft(context.boundaryPanel.getElement());
    boundaryOffsetY = widgetLocation.getTop()
        + DOMUtil.getBorderTop(context.boundaryPanel.getElement());

    dropTargetClientWidth = DOMUtil.getClientWidth(boundaryPanel.getElement());
    dropTargetClientHeight = DOMUtil.getClientHeight(boundaryPanel.getElement());
  }

  /**
   * Whether or not dropping on the boundary panel is permitted.
   * 
   * @return <code>true</code> if dropping on the boundary panel is allowed
   */
  public boolean getBehaviorBoundaryPanelDrop() {
    return boundaryDropController.getBehaviorBoundaryPanelDrop();
  }

  /**
   * Determine whether or not this controller automatically creates a drag proxy
   * for each drag operation. Whether or not a drag proxy is used is ultimately
   * determined by the return value of {@link #maybeNewDraggableProxy(Widget)}
   * 
   * @return <code>true</code> if drag proxy behavior is enabled
   */
  public boolean getBehaviorDragProxy() {
    return dragProxyEnabled;
  }

  /**
   * @deprecated Use {@link #getBehaviorDragProxy()} instead.
   */
  public boolean isDragProxyEnabled() {
    return getBehaviorDragProxy();
  }

  public void previewDragEnd() throws VetoDragException {
    // Does the DropController allow the drop?
    try {
      context.dropController.onPreviewDrop(context);
      context.finalDropController = context.dropController;
    } catch (VetoDragException ex) {
      context.finalDropController = null;
      throw ex;
    }
  }

  /**
   * Register a new DropController, representing a new drop target, with this
   * drag controller.
   * 
   * @see #unregisterDropController(DropController)
   * 
   * @param dropController the controller to register
   */
  public void registerDropController(DropController dropController) {
    dropControllerList.add(dropController);
  }

  public void resetCache() {
    super.resetCache();
    dropControllerCollection.resetCache(boundaryPanel, context.draggable);
  }

  /**
   * Set whether or not widgets may be dropped anywhere on the boundary panel.
   * Set to <code>false</code> when you only want explicitly registered drop
   * controllers to accept drops. Defaults to <code>true</code>.
   * 
   * @param allowDroppingOnBoundaryPanel <code>true</code> to allow dropping
   */
  public void setBehaviorBoundaryPanelDrop(boolean allowDroppingOnBoundaryPanel) {
    boundaryDropController.setBehaviorBoundaryPanelDrop(allowDroppingOnBoundaryPanel);
  }

  /**
   * Set whether or not this controller should automatically create a drag proxy
   * for each drag operation. Whether or not a drag proxy is used is ultimately
   * determined by the return value of {@link #maybeNewDraggableProxy(Widget)}.
   * 
   * @param dragProxyEnabled <code>true</code> to enable drag proxy behavior
   */
  public void setBehaviorDragProxy(boolean dragProxyEnabled) {
    this.dragProxyEnabled = dragProxyEnabled;
  }

  /**
   * @deprecated Use {@link #setBehaviorDragProxy(boolean)} instead.
   */
  public void setDragProxyEnabled(boolean dragProxyEnabled) {
    setBehaviorDragProxy(dragProxyEnabled);
  }

  /**
   * Unregister a DropController from this drag controller.
   * 
   * @see #registerDropController(DropController)
   * 
   * @param dropController the controller to register
   */
  public void unregisterDropController(DropController dropController) {
    dropControllerList.remove(dropController);
  }

  /**
   * @deprecated Use {@link #newDragProxy(DragContext)} and {@link #setBehaviorDragProxy(boolean)} instead.
   */
  protected final Widget maybeNewDraggableProxy(Widget draggable) {
    throw new UnsupportedOperationException();
  }

  /**
   * Create a new BoundaryDropController to manage our boundary panel as a drop
   * target. To ensure that draggable widgets can only be dropped on registered
   * drop targets, set <code>allowDroppingOnBoundaryPanel</code> to <code>false</code>.
   *
   * @param boundaryPanel the panel to which our drag-and-drop operations are constrained
   * @param allowDroppingOnBoundaryPanel whether or not dropping is allowed on the boundary panel
   * @return the new BoundaryDropController
   */
  protected BoundaryDropController newBoundaryDropController(AbsolutePanel boundaryPanel,
      boolean allowDroppingOnBoundaryPanel) {
    return new BoundaryDropController(boundaryPanel, allowDroppingOnBoundaryPanel);
  }

  /**
   * Called by {@link PickupDragController#dragStart(Widget)} to allow subclasses to
   * provide their own drag proxies.
   * 
   * @param context the current drag context
   * @return a new drag proxy
   */
  protected Widget newDragProxy(DragContext context) {
    AbsolutePanel container = new AbsolutePanel();
    DOM.setStyleAttribute(container.getElement(), "overflow", "visible");

    WidgetArea draggableArea = new WidgetArea(context.draggable, null);
    for (Iterator iterator = context.selectedWidgets.iterator(); iterator.hasNext();) {
      Widget widget = (Widget) iterator.next();
      WidgetArea widgetArea = new WidgetArea(widget, null);
      Widget proxy = new SimplePanel();
      proxy.setPixelSize(widget.getOffsetWidth(), widget.getOffsetHeight());
      proxy.addStyleName(PRIVATE_CSS_PROXY);
      container.add(proxy, widgetArea.getLeft() - draggableArea.getLeft(), widgetArea.getTop()
          - draggableArea.getTop());
    }

    return container;
  }

  /**
   * Restore the selected widgets to their original location.
   * @see #saveSelectedWidgetsLocationAndStyle()
   * @see #restoreSelectedWidgetsStyle()
   */
  protected void restoreSelectedWidgetsLocation() {
    for (Iterator iterator = context.selectedWidgets.iterator(); iterator.hasNext();) {
      Widget widget = (Widget) iterator.next();
      SavedWidgetInfo info = (SavedWidgetInfo) savedWidgetInfoMap.get(widget);

      // TODO simplify after enhancement for issue 1112 provides InsertPanel interface
      // http://code.google.com/p/google-web-toolkit/issues/detail?id=1112
      if (info.initialDraggableParent instanceof AbsolutePanel) {
        ((AbsolutePanel) info.initialDraggableParent).add(widget,
            info.initialDraggableParentLocation.getLeft(),
            info.initialDraggableParentLocation.getTop());
      } else if (info.initialDraggableParent instanceof HorizontalPanel) {
        ((HorizontalPanel) info.initialDraggableParent).insert(widget, info.initialDraggableIndex);
      } else if (info.initialDraggableParent instanceof VerticalPanel) {
        ((VerticalPanel) info.initialDraggableParent).insert(widget, info.initialDraggableIndex);
      } else if (info.initialDraggableParent instanceof FlowPanel) {
        ((FlowPanel) info.initialDraggableParent).insert(widget, info.initialDraggableIndex);
      } else if (info.initialDraggableParent instanceof SimplePanel) {
        ((SimplePanel) info.initialDraggableParent).setWidget(widget);
      } else {
        throw new RuntimeException("Unable to handle initialDraggableParent "
            + GWT.getTypeName(info.initialDraggableParent));
      }
    }
  }

  /**
   * Restore the selected widgets with their original style.
   * @see #saveSelectedWidgetsLocationAndStyle()
   * @see #restoreSelectedWidgetsLocation()
   */
  protected void restoreSelectedWidgetsStyle() {
    for (Iterator iterator = context.selectedWidgets.iterator(); iterator.hasNext();) {
      Widget widget = (Widget) iterator.next();
      SavedWidgetInfo info = (SavedWidgetInfo) savedWidgetInfoMap.get(widget);

      if (info.initialDraggableMargin != null && info.initialDraggableMargin.length() != 0) {
        DOM.setStyleAttribute(widget.getElement(), "margin", info.initialDraggableMargin);
      }
    }
  }

  /**
   * Save the selected widgets' current location in case they much
   * be restored due to a canceled drop.
   * @see #restoreSelectedWidgetsLocation()
   */
  protected void saveSelectedWidgetsLocationAndStyle() {
    savedWidgetInfoMap = new HashMap();
    for (Iterator iterator = context.selectedWidgets.iterator(); iterator.hasNext();) {
      Widget widget = (Widget) iterator.next();

      SavedWidgetInfo info = new SavedWidgetInfo();
      info.initialDraggableParent = widget.getParent();

      // TODO simplify after enhancement for issue 1112 provides InsertPanel interface
      // http://code.google.com/p/google-web-toolkit/issues/detail?id=1112
      if (info.initialDraggableParent instanceof AbsolutePanel) {
        info.initialDraggableParentLocation = new WidgetLocation(widget,
            info.initialDraggableParent);
      } else if (info.initialDraggableParent instanceof HorizontalPanel) {
        info.initialDraggableIndex = ((HorizontalPanel) info.initialDraggableParent).getWidgetIndex(widget);
      } else if (info.initialDraggableParent instanceof VerticalPanel) {
        info.initialDraggableIndex = ((VerticalPanel) info.initialDraggableParent).getWidgetIndex(widget);
      } else if (info.initialDraggableParent instanceof FlowPanel) {
        info.initialDraggableIndex = ((FlowPanel) info.initialDraggableParent).getWidgetIndex(widget);
      } else if (info.initialDraggableParent instanceof SimplePanel) {
        // save nothing
      } else {
        throw new RuntimeException(
            "Unable to handle 'initialDraggableParent instanceof "
                + GWT.getTypeName(info.initialDraggableParent)
                + "'; Please create your own DragController and override saveDraggableLocationAndStyle() and restoreDraggableLocation()");
      }

      info.initialDraggableMargin = DOM.getStyleAttribute(widget.getElement(), "margin");
      if (info.initialDraggableMargin != null && info.initialDraggableMargin.length() != 0) {
        DOM.setStyleAttribute(widget.getElement(), "margin", "0px");
      }
      savedWidgetInfoMap.put(widget, info);
    }
  }

  private DropController getIntersectDropController(int x, int y) {
    DropController dropController = dropControllerCollection.getIntersectDropController(x, y);
    return dropController != null ? dropController : boundaryDropController;
  }
}
