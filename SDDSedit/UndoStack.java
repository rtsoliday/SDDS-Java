/*************************************************************************\
* Copyright (c) 2026 The University of Chicago, as Operator of Argonne
* National Laboratory.
* This file is distributed subject to a Software License Agreement found
* in the file LICENSE that is included with this distribution.
\*************************************************************************/

package SDDS.java.SDDSedit;

import java.util.ArrayList;
import java.util.List;

/** The history of changes to a document. */
final class UndoStack {
    /** One change that can be undone and redone. */
    interface Command {
        void undo();

        void redo();

        String text();
    }

    private final List<Command> commands = new ArrayList<Command>();
    private int position;
    private final Runnable changed;

    UndoStack(Runnable changed) {
        this.changed = changed;
    }

    /** Add a command that has already been applied, discarding anything to redo. */
    void pushDone(Command command) {
        while (commands.size() > position)
            commands.remove(commands.size() - 1);
        commands.add(command);
        position = commands.size();
        changed.run();
    }

    /** Apply a command and add it. */
    void push(Command command) {
        command.redo();
        pushDone(command);
    }

    boolean canUndo() {
        return position > 0;
    }

    boolean canRedo() {
        return position < commands.size();
    }

    String undoText() {
        return canUndo() ? commands.get(position - 1).text() : "";
    }

    String redoText() {
        return canRedo() ? commands.get(position).text() : "";
    }

    void undo() {
        if (canUndo()) {
            commands.get(--position).undo();
            changed.run();
        }
    }

    void redo() {
        if (canRedo()) {
            commands.get(position++).redo();
            changed.run();
        }
    }

    void clear() {
        commands.clear();
        position = 0;
        changed.run();
    }
}
