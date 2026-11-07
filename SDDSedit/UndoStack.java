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

        /** About how many bytes the command keeps alive. */
        long size();
    }

    private final List<Command> commands = new ArrayList<Command>();
    private int position;
    private final Runnable changed;
    private long limit = Long.MAX_VALUE;
    private long total;

    UndoStack(Runnable changed) {
        this.changed = changed;
    }

    /** Set how many bytes the history may keep alive. The oldest steps go first, never the newest. */
    void setLimit(long bytes) {
        limit = bytes;
    }

    /** Steps that can be undone now. */
    int undoCount() {
        return position;
    }

    /** Add a command that has already been applied, discarding anything to redo. */
    void pushDone(Command command) {
        while (commands.size() > position)
            total -= commands.remove(commands.size() - 1).size();
        commands.add(command);
        total += command.size();
        while (total > limit && commands.size() > 1)
            total -= commands.remove(0).size();
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
        total = 0;
        changed.run();
    }
}
