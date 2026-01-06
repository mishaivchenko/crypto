import * as React from "react";
import * as TabsPrimitive from "@radix-ui/react-tabs";
import { twMerge } from "tailwind-merge";

export const Tabs = TabsPrimitive.Root;

export const TabsList = ({ className, ...props }: React.ComponentProps<typeof TabsPrimitive.List>) => (
    <TabsPrimitive.List
        className={twMerge("inline-flex h-10 items-center justify-center rounded-2xl bg-muted p-1 text-muted-foreground", className)}
        {...props}
    />
);

export const TabsTrigger = ({ className, ...props }: React.ComponentProps<typeof TabsPrimitive.Trigger>) => (
    <TabsPrimitive.Trigger
        className={twMerge(
            "inline-flex items-center justify-center whitespace-nowrap rounded-xl px-3 py-1.5 text-sm font-medium ring-offset-background transition-all focus-visible:outline-none focus-visible:ring-2 data-[state=active]:bg-white data-[state=active]:text-foreground",
            className
        )}
        {...props}
    />
);

export const TabsContent = ({ className, ...props }: React.ComponentProps<typeof TabsPrimitive.Content>) => (
    <TabsPrimitive.Content className={twMerge("mt-4 focus-visible:outline-none", className)} {...props} />
);
