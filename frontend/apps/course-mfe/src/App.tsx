import { Route, Routes, useParams } from 'react-router-dom';
import { Typography } from '@mui/material';
import { CourseListPage } from './pages/CourseListPage';
import { CourseDetailPage, CourseNotFound } from './pages/CourseDetailPage';

/** /courses/:id 의 id를 숫자로 바꾼다. 숫자가 아니면 API를 부르지 않고 바로 안내한다. */
function CourseDetailRoute() {
  const id = Number(useParams().id);
  if (!Number.isInteger(id) || id <= 0) return <CourseNotFound />;
  return <CourseDetailPage key={id} id={id} />;
}

export function App() {
  return (
    <Routes>
      <Route
        path="/"
        element={
          <>
            <Typography variant="h5" component="h1" gutterBottom>
              교육과정 목록
            </Typography>
            <CourseListPage />
          </>
        }
      />
      <Route path="/courses/:id" element={<CourseDetailRoute />} />
      <Route path="*" element={<CourseNotFound />} />
    </Routes>
  );
}
