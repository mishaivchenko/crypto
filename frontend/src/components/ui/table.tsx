import * as React from "react";
import { twMerge } from "tailwind-merge";

export function Table({ className, ...props }: React.HTMLAttributes<HTMLTableElement>) {
    return <table className={twMerge("w-full caption-bottom text-sm", className)} {...props} />;
}

export function THead(props: React.HTMLAttributes<HTMLTableSectionElement>) {
    return <thead {...props} />;
}
export function TBody(props: React.HTMLAttributes<HTMLTableSectionElement>) {
    return <tbody {...props} />;
}
export function TR({ className, ...props }: React.HTMLAttributes<HTMLTableRowElement>) {
    return <tr className={twMerge("border-b last:border-b-0", className)} {...props} />;
}
export function TH({ className, ...props }: React.ThHTMLAttributes<HTMLTableCellElement>) {
    return <th className={twMerge("h-10 px-3 text-left align-middle font-medium text-muted-foreground", className)} {...props} />;
}
export function TD({ className, ...props }: React.TdHTMLAttributes<HTMLTableCellElement>) {
    return <td className={twMerge("p-3 align-middle", className)} {...props} />;
}
