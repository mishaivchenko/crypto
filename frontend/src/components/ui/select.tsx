import * as React from "react";
import * as SelectPrimitive from "@radix-ui/react-select";
import { twMerge } from "tailwind-merge";

export function Select(props: React.ComponentProps<typeof SelectPrimitive.Root>) {
    return <SelectPrimitive.Root {...props} />;
}

export function SelectTrigger(props: React.ComponentProps<typeof SelectPrimitive.Trigger>) {
    return (
        <SelectPrimitive.Trigger
            {...props}
            className={twMerge(
                "inline-flex h-9 items-center justify-between rounded-xl border border-input bg-white px-3 text-sm",
                props.className
            )}
        />
    );
}
export const SelectValue = SelectPrimitive.Value;

export function SelectContent(props: React.ComponentProps<typeof SelectPrimitive.Content>) {
    return (
        <SelectPrimitive.Portal>
            <SelectPrimitive.Content
                sideOffset={4}
                className={twMerge("z-50 overflow-hidden rounded-xl border bg-white shadow-md", props.className)}
                {...props}
            >
                <SelectPrimitive.Viewport className="p-1">{props.children}</SelectPrimitive.Viewport>
            </SelectPrimitive.Content>
        </SelectPrimitive.Portal>
    );
}

export function SelectItem({ className, ...props }: React.ComponentProps<typeof SelectPrimitive.Item>) {
    return (
        <SelectPrimitive.Item
            className={twMerge(
                "relative flex cursor-pointer select-none items-center rounded-lg px-3 py-2 text-sm outline-none data-[highlighted]:bg-muted",
                className
            )}
            {...props}
        />
    );
}
