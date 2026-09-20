"use client";

import { Component, type ErrorInfo, type ReactNode } from "react";
import { ErrorState } from "./error-state";

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
}

/**
 * Catches render errors within a screen so a component bug surfaces as an
 * inline, retryable card instead of a blank page.
 */
export class ScreenErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error("Screen render error", error, info.componentStack);
  }

  render() {
    if (this.state.hasError) {
      return (
        <ErrorState
          title="This view failed to render"
          message="An unexpected error occurred. Retry, or reload the page."
          onRetry={() => this.setState({ hasError: false })}
        />
      );
    }
    return this.props.children;
  }
}
