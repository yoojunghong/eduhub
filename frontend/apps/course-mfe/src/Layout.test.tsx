import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Layout } from './Layout';
import { routerFuture } from './routerFuture';

const renderAt = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]} future={routerFuture}>
      <Layout>
        <p>본문</p>
      </Layout>
    </MemoryRouter>,
  );

test.each([
  ['/', '교육과정'],
  ['/courses/3', '교육과정'],
  ['/admin', '과정 관리'],
  ['/admin/courses/3', '과정 관리'],
])('%s 에서는 %s 탭이 선택된다', (path, tab) => {
  renderAt(path);

  expect(screen.getByRole('tab', { name: tab })).toHaveAttribute('aria-selected', 'true');
  expect(screen.getByText('본문')).toBeInTheDocument();
});
