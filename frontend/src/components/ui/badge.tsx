import * as React from "react";
import { twMerge } from "tailwind-merge";

type Variant = "default" | "success" | "danger" | "warning" | "muted";

const variants: Record<Variant, string> = {
    default: "bg-muted text-foreground",
    success: "bg-green-100 text-green-700 border-green-200",
    danger: "bg-red-100 text-red-700 border-red-200",
    warning: "bg-yellow-100 text-yellow-800 border-yellow-200",
    muted: "bg-muted text-muted-foreground"
};

export function Badge({
                          variant = "default",
                          className,
                          ...props
                      }: React.HTMLAttributes<HTMLSpanElement> & { variant?: Variant }) {
    return (
        <span
            className={twMerge(
                "inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium",
                variants[variant],
                className
            )}
            {...props}
        />
    );
}
